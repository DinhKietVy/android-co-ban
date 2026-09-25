import os
import base64
import json
import time
import mimetypes
import pyodbc
from dotenv import load_dotenv
from openai import OpenAI

# pip install -U openai python-dotenv pyodbc

load_dotenv()

api_key = os.getenv("OPENAI_API_KEY")
if not api_key:
    raise ValueError("Không tìm thấy OPENAI_API_KEY trong file .env")

client = OpenAI(api_key=api_key)

# GPT-5.5. Có thể thêm model fallback nếu project của bạn có quyền dùng model khác.
_MODEL_FALLBACK = [
    "gpt-5.5",
]
MODEL_NAME = _MODEL_FALLBACK[0]

# Các từ khóa gợi ý người dùng muốn AI ĐỌC NỘI DUNG file
_READ_KEYWORDS = (
    "tóm tắt", "đọc", "phân tích", "dịch", "nội dung", "xem file",
    "summarize", "read", "analyze", "translate", "content"
)

# ─────────────────────────────────────────────
# Database connection (SQL Server via pyodbc)
# ─────────────────────────────────────────────
def get_db_connection():
    server   = os.getenv("DB_SERVER",   "localhost")
    database = os.getenv("DB_NAME",     "ANDROID")
    db_user  = os.getenv("DB_USER",     "myuser")
    password = os.getenv("DB_PASSWORD", "123456")
    conn_str = (
        "DRIVER={ODBC Driver 17 for SQL Server};"
        f"SERVER={server};DATABASE={database};"
        f"UID={db_user};PWD={password};"
        "TrustServerCertificate=yes;Encrypt=no;"
    )
    return pyodbc.connect(conn_str)


# ─────────────────────────────────────────────
# Retry + model fallback helper
# ─────────────────────────────────────────────
def _call_with_fallback(call_fn):
    """
    Thử call_fn(model) lần lượt qua _MODEL_FALLBACK.
    Mỗi model retry 2 lần khi gặp lỗi tạm thời (503/429).
    """
    last_err = None
    for model in _MODEL_FALLBACK:
        for attempt in range(2):
            try:
                return call_fn(model)
            except Exception as e:
                err_str = str(e)
                if any(c in err_str for c in ("503", "429", "UNAVAILABLE", "overloaded")):
                    last_err = e
                    wait = 2 ** attempt
                    print(f"⚠️  {model} lỗi tạm thời (attempt {attempt+1}): {err_str[:80]}. Retry sau {wait}s...")
                    time.sleep(wait)
                else:
                    raise  # Lỗi cứng (400, 403...) → không retry
        print(f"⚠️  {model} không khả dụng, chuyển model tiếp theo...")
    raise RuntimeError(f"Tất cả model đều thất bại. Lỗi cuối: {last_err}")


# ─────────────────────────────────────────────
# File metadata listing
# ─────────────────────────────────────────────
def get_user_files_metadata(username: str) -> list[dict]:
    """Lấy danh sách metadata file của user từ filesystem."""
    # __file__ = .../AI/chatbot/openai_service.py
    # chatbot_dir -> AI_dir -> workspace_dir
    chatbot_dir    = os.path.dirname(os.path.abspath(__file__))
    ai_dir         = os.path.dirname(chatbot_dir)
    workspace_dir  = os.path.dirname(ai_dir)
    user_data_path = os.path.join(workspace_dir, "BackEnd", "data", username)

    if not os.path.exists(user_data_path):
        return []

    file_list = []
    for root, dirs, files in os.walk(user_data_path):
        dirs[:] = [d for d in dirs if d not in (".AI", "AI", "trash")]
        for file_name in files:
            if file_name.endswith(".meta.json"):
                continue
            full_path = os.path.join(root, file_name)
            rel_path  = os.path.relpath(full_path, user_data_path).replace("\\", "/")
            stat      = os.stat(full_path)

            tags = []
            json_result = os.path.join(workspace_dir, "AI", f"result_{file_name}.json")
            if os.path.exists(json_result):
                try:
                    with open(json_result, "r", encoding="utf-8") as jf:
                        jdata = json.load(jf)
                    tags = jdata.get("tags", [])
                except Exception:
                    pass

            file_list.append({
                "filePath": rel_path,
                "name":     file_name,
                "size":     stat.st_size,
                "createdAt": str(int(stat.st_ctime * 1000)),
                "tags":     tags,
            })
    return file_list


def get_user_structure(username: str) -> str:
    """
    Trả về chuỗi mô tả cấu trúc file + thư mục của user.
    Dùng để inject vào context_prefix cho AI biết chính xác path.
    """
    chatbot_dir    = os.path.dirname(os.path.abspath(__file__))
    ai_dir         = os.path.dirname(chatbot_dir)
    workspace_dir  = os.path.dirname(ai_dir)
    user_data_path = os.path.join(workspace_dir, "BackEnd", "data", username)

    if not os.path.exists(user_data_path):
        return "  (chưa có file)"

    lines = []
    for root, dirs, files in os.walk(user_data_path):
        dirs[:] = sorted(d for d in dirs if d not in (".AI", "AI", "trash"))
        rel_root = os.path.relpath(root, user_data_path).replace("\\", "/")
        if rel_root == ".":
            rel_root = ""
        # Liệt kê thư mục con
        for d in dirs:
            folder_rel = (rel_root + "/" + d).lstrip("/")
            lines.append(f"  [folder] {folder_rel}")
        # Liệt kê file
        for f in sorted(files):
            if f.endswith(".meta.json"):
                continue
            file_rel = (rel_root + "/" + f).lstrip("/")
            lines.append(f"  [file]   {file_rel}")

    return "\n".join(lines) if lines else "  (chưa có file)"


# ─────────────────────────────────────────────
# Build file content for inline analysis
# ─────────────────────────────────────────────
def _build_file_input_part(abs_path: str) -> dict:
    """
    Tạo input part tương thích với OpenAI Responses API.

    - Ảnh  -> input_image với data URL base64
    - PDF  -> input_file với data URL base64
    - Text -> input_text chứa nội dung file
    - File khác -> input_text thông báo metadata
    """
    mime_type = mimetypes.guess_type(abs_path)[0] or "application/octet-stream"

    with open(abs_path, "rb") as f:
        data = f.read()

    encoded = base64.b64encode(data).decode("utf-8")
    filename = os.path.basename(abs_path)

    if mime_type.startswith("image/"):
        return {
            "type": "input_image",
            "image_url": f"data:{mime_type};base64,{encoded}",
            "detail": "auto",
        }

    if mime_type == "application/pdf":
        return {
            "type": "input_file",
            "filename": filename,
            "file_data": f"data:application/pdf;base64,{encoded}",
        }

    if mime_type.startswith("text/") or mime_type in (
        "application/json",
        "application/xml",
        "text/csv",
    ):
        text = data.decode("utf-8", errors="replace")
        return {
            "type": "input_text",
            "text": f"[Nội dung file - {filename}]\n{text}",
        }

    return {
        "type": "input_text",
        "text": f"[File đính kèm: {filename}, loại: {mime_type}]",
    }


# ─────────────────────────────────────────────
# Tool declarations (Function Calling)
# ─────────────────────────────────────────────
def _get_tools() -> list[dict]:
    return [
        {
            "type": "function",
            "name": "create_folder",
            "description": "Tạo một thư mục mới trong hệ thống file của người dùng",
            "parameters": {
                "type": "object",
                "properties": {
                    "folderName": {"type": "string", "description": "Tên thư mục cần tạo"},
                    "targetPath": {"type": "string", "description": "Đường dẫn thư mục cha, mặc định '' là thư mục gốc"},
                },
                "required": ["folderName"],
            },
        },
        {
            "type": "function",
            "name": "move_files",
            "description": "Di chuyển một hoặc nhiều file vào một thư mục đích",
            "parameters": {
                "type": "object",
                "properties": {
                    "filePaths":    {"type": "array",  "items": {"type": "string"}, "description": "Danh sách đường dẫn file cần di chuyển"},
                    "targetFolder": {"type": "string", "description": "Đường dẫn thư mục đích"},
                },
                "required": ["filePaths", "targetFolder"],
            },
        },
        {
            "type": "function",
            "name": "delete_file",
            "description": "Xóa (đưa vào thùng rác) một file của người dùng",
            "parameters": {
                "type": "object",
                "properties": {
                    "filePath": {"type": "string", "description": "Đường dẫn file cần xóa"},
                },
                "required": ["filePath"],
            },
        },
        {
            "type": "function",
            "name": "list_files",
            "description": "Liệt kê file và thư mục trong một đường dẫn của người dùng",
            "parameters": {
                "type": "object",
                "properties": {
                    "folderPath": {"type": "string", "description": "Đường dẫn thư mục, '' là thư mục gốc"},
                },
            },
        },
    ]


# ─────────────────────────────────────────────
# Function execution
# ─────────────────────────────────────────────
def _execute_function(username: str, fn_name: str, args: dict) -> dict:
    import shutil

    chatbot_dir    = os.path.dirname(os.path.abspath(__file__))
    ai_dir         = os.path.dirname(chatbot_dir)
    workspace_dir  = os.path.dirname(ai_dir)
    user_data_path = os.path.join(workspace_dir, "BackEnd", "data", username)

    def safe_path(rel: str) -> str:
        abs_p = os.path.normpath(os.path.join(user_data_path, rel or ""))
        if not abs_p.startswith(user_data_path):
            raise PermissionError("Đường dẫn không hợp lệ")
        return abs_p

    try:
        if fn_name == "create_folder":
            folder_name = args.get("folderName", "")
            target_path = args.get("targetPath", "")
            safe_name   = "".join(c for c in folder_name if c.isalnum() or c in " _-.")
            if not safe_name:
                return {"message": "Tên thư mục không hợp lệ", "affectedItems": []}
            new_dir = os.path.join(safe_path(target_path), safe_name)
            if os.path.exists(new_dir):
                return {"message": f"Thư mục '{safe_name}' đã tồn tại", "affectedItems": [safe_name]}
            os.makedirs(new_dir)
            return {"message": f"Đã tạo thư mục '{safe_name}' thành công", "affectedItems": [safe_name]}

        elif fn_name == "move_files":
            file_paths    = args.get("filePaths", [])
            target_folder = args.get("targetFolder", "")
            dest_dir      = safe_path(target_folder)
            # Tự tạo thư mục đích nếu chưa tồn tại (hỗ trợ flow tạo folder rồi move ngay)
            if not os.path.isdir(dest_dir):
                os.makedirs(dest_dir, exist_ok=True)
            moved = []
            not_found = []
            for fp in file_paths:
                src = safe_path(fp)
                if os.path.exists(src):
                    dst = os.path.join(dest_dir, os.path.basename(src))
                    shutil.move(src, dst)
                    moved.append(os.path.basename(src))
                else:
                    not_found.append(fp)
            msg = f"Đã di chuyển {len(moved)} file vào '{target_folder}'"
            if not_found:
                msg += f". Không tìm thấy: {', '.join(not_found)}"
            return {"message": msg, "affectedItems": moved}

        elif fn_name == "delete_file":
            fp        = args.get("filePath", "")
            abs_fp    = safe_path(fp)
            trash_dir = safe_path("trash")
            os.makedirs(trash_dir, exist_ok=True)
            if not os.path.exists(abs_fp):
                return {"message": "File không tồn tại", "affectedItems": []}
            file_name  = os.path.basename(abs_fp)
            ts         = int(time.time() * 1000)
            trash_path = os.path.join(trash_dir, f"{ts}_{file_name}")
            shutil.move(abs_fp, trash_path)
            with open(trash_path + ".meta.json", "w") as mf:
                json.dump({"originalPath": fp, "deletedAt": ts}, mf)
            return {"message": f"Đã đưa '{file_name}' vào thùng rác", "affectedItems": [file_name]}

        elif fn_name == "list_files":
            folder_path = args.get("folderPath", "")
            target_dir  = safe_path(folder_path)
            if not os.path.isdir(target_dir):
                return {"message": "Thư mục không tồn tại", "affectedItems": []}

            entries = os.listdir(target_dir)
            folders = []
            files   = []
            for entry in entries:
                if entry.endswith(".meta.json"):
                    continue
                full = os.path.join(target_dir, entry)
                # relative path từ user_data_path để AI dùng được trong move_files
                rel  = os.path.relpath(full, user_data_path).replace("\\", "/")
                if os.path.isdir(full):
                    folders.append(f"[folder] {rel}")
                else:
                    files.append(f"[file] {rel}")

            all_items   = folders + files
            rel_paths   = [i.split("] ", 1)[1] for i in all_items]  # chỉ path, bỏ tag
            summary     = "\n".join(all_items) if all_items else "(trống)"
            prefix      = f"Thư mục '{folder_path or '/'}' chứa:\n{summary}"
            return {"message": prefix, "affectedItems": rel_paths}

        return {"message": f"Hàm '{fn_name}' chưa được hỗ trợ", "affectedItems": []}

    except PermissionError as e:
        return {"message": str(e), "affectedItems": []}
    except Exception as e:
        return {"message": f"Lỗi khi thực thi '{fn_name}': {str(e)}", "affectedItems": []}


# ─────────────────────────────────────────────
# Core chat function (OpenAI Responses API)
# ─────────────────────────────────────────────
def chat_with_gemini(
    username: str,
    prompt: str,
    history: list[dict] | None = None,
    file_path: str | None = None,
) -> dict:
    """
    Gửi tin nhắn đến GPT-5.5 qua OpenAI Responses API.

    Multi-turn:
    - history được replay để lấy previous_response_id.
    - turn hiện tại tiếp tục từ previous_response_id đó.

    filePath:
    - Nếu prompt yêu cầu đọc/phân tích/dịch file -> gửi nội dung file cho model.
    - Nếu là lệnh thao tác file -> chỉ đưa tên/path file vào context.
    """
    tools = _get_tools()

    system_instr = (
        "Bạn là trợ lý AI thông minh tích hợp trong hệ thống quản lý file. "
        "Bạn có thể trả lời câu hỏi thông thường, phân tích tài liệu, "
        "và thực hiện thao tác quản lý file (tạo thư mục, di chuyển, xóa file) "
        "khi người dùng yêu cầu. Trả lời bằng tiếng Việt, thân thiện và ngắn gọn.\n\n"
        "QUAN TRỌNG về đường dẫn file khi gọi tool:\n"
        "- Trong [Ngữ cảnh hệ thống] đã liệt kê sẵn các file/thư mục hiện có "
        "kèm đường dẫn tương đối.\n"
        "- Khi gọi move_files, filePaths PHẢI dùng ĐÚNG đường dẫn tương đối đó "
        "(ví dụ: 'a.pdf', 'abcd/report.pdf').\n"
        "- Khi gọi create_folder, targetPath là '' nếu tạo ở thư mục gốc.\n"
        "- KHÔNG cần gọi list_files nếu danh sách file đã có trong ngữ cảnh.\n"
        "- Nếu người dùng nói 'tài liệu' mà không chỉ rõ tên, hãy dùng filePath "
        "được đề cập trong yêu cầu.\n"
        "- Khi cần thực hiện nhiều bước phụ thuộc nhau (ví dụ: tạo folder rồi mới move file vào), "
        "hãy gọi từng tool MỘT LƯỢT MỘT, không gọi cả hai cùng lúc. "
        "Đợi kết quả bước trước xong mới gọi bước tiếp theo."
    )

    # ── Bước 1: Replay history để lấy previous_response_id ──
    prev_id = None

    if history:
        for turn in history:
            role = turn.get("role", "user")
            parts_raw = turn.get("parts", [])

            text = " ".join(
                p.get("text", "")
                for p in parts_raw
                if isinstance(p, dict) and "text" in p
            ).strip()

            if not text or role != "user":
                continue

            def _replay(model, _text=text, _prev=prev_id):
                kwargs = {
                    "model": model,
                    "input": _text,
                    "instructions": system_instr,
                    "reasoning": {"effort": "low"},
                }
                if _prev:
                    kwargs["previous_response_id"] = _prev
                return client.responses.create(**kwargs)

            resp = _call_with_fallback(_replay)
            prev_id = resp.id

    # ── Bước 2: Xây dựng input cho turn hiện tại ──
    chatbot_dir    = os.path.dirname(os.path.abspath(__file__))
    ai_dir         = os.path.dirname(chatbot_dir)
    workspace_dir  = os.path.dirname(ai_dir)
    user_data_path = os.path.join(workspace_dir, "BackEnd", "data", username)

    # Lấy cấu trúc file + thư mục hiện có của user để inject vào ngữ cảnh
    structure_hint = get_user_structure(username)
    context_prefix  = (
        f"[Ngữ cảnh hệ thống]\n"
        f"- Username: {username}\n"
        f"- Cấu trúc file/thư mục hiện có của user "
        f"(dùng đúng đường dẫn này khi gọi tool):\n"
        f"{structure_hint}\n\n"
    )

    current_input = context_prefix + prompt

    if file_path:
        abs_path = os.path.normpath(
            os.path.join(user_data_path, file_path)
        )
        file_exists = (
            abs_path.startswith(user_data_path)
            and os.path.isfile(abs_path)
        )

        want_read = any(
            kw in prompt.lower()
            for kw in _READ_KEYWORDS
        )

        if want_read and file_exists:
            try:
                file_part = _build_file_input_part(abs_path)

                current_input = [
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "input_text",
                                "text": (
                                    context_prefix
                                    + f"Câu hỏi: {prompt}"
                                ),
                            },
                            file_part,
                        ],
                    }
                ]
            except Exception as e:
                current_input = (
                    context_prefix
                    + f"[File '{file_path}' không đọc được: {e}]\n\n"
                    + f"Yêu cầu: {prompt}"
                )
        else:
            file_note = (
                f"[File được đề cập: '{file_path}'"
                + (" — tồn tại." if file_exists else " — không tìm thấy.")
                + "]\n\n"
            )
            current_input = context_prefix + file_note + prompt

    # ── Bước 3: Gửi turn đầu tiên ──
    def _first_turn(model):
        kwargs = {
            "model": model,
            "input": current_input,
            "instructions": system_instr,
            "tools": tools,
            "reasoning": {"effort": "medium"},
        }

        if prev_id:
            kwargs["previous_response_id"] = prev_id

        return client.responses.create(**kwargs)

    response = _call_with_fallback(_first_turn)

    # ── Bước 4: Tool-call loop ──
    executed_actions: list[str] = []
    affected_all: list[str] = []

    MAX_TURNS = 5

    for _ in range(MAX_TURNS):
        # Responses API trả function calls trong response.output.
        fc_steps = [
            item
            for item in response.output
            if getattr(item, "type", None) == "function_call"
        ]

        if not fc_steps:
            break

        function_outputs = []

        for step in fc_steps:
            fn_name = step.name

            try:
                fn_args = (
                    json.loads(step.arguments)
                    if isinstance(step.arguments, str)
                    else (step.arguments or {})
                )
            except json.JSONDecodeError:
                fn_args = {}

            action_result = _execute_function(
                username,
                fn_name,
                fn_args,
            )

            executed_actions.append(fn_name)
            affected_all.extend(
                action_result.get("affectedItems", [])
            )

            function_outputs.append({
                "type": "function_call_output",
                "call_id": step.call_id,
                "output": json.dumps(
                    action_result,
                    ensure_ascii=False,
                ),
            })

        # Gửi kết quả tool về Responses API.
        response = client.responses.create(
            model=MODEL_NAME,
            previous_response_id=response.id,
            instructions=system_instr,
            input=function_outputs,
            tools=tools,
            reasoning={"effort": "medium"},
        )

    # ── Bước 5: Trả về kết quả ──
    final_text = response.output_text or "Đã thực hiện xong."

    if executed_actions:
        return {
            "role": "model",
            "text": final_text,
            "actionExecuted": executed_actions[-1],
            "actionsExecuted": executed_actions,
            "affectedItems": list(set(affected_all)),
        }

    return {
        "role": "model",
        "text": final_text,
    }


# ─────────────────────────────────────────────
# Semantic Search
# ─────────────────────────────────────────────
def semantic_search(username: str, query: str) -> list[dict]:
    file_list = get_user_files_metadata(username)
    if not file_list:
        return []
    if len(file_list) > 200:
        file_list = file_list[:200]

    search_prompt = (
        f"Dưới đây là danh sách file của người dùng dưới dạng JSON:\n"
        f"{json.dumps(file_list, ensure_ascii=False)}\n\n"
        f"Người dùng yêu cầu: '{query}'\n\n"
        "Tìm các file khớp với yêu cầu (dựa trên tên, tags, ngày tháng). "
        "CHỈ trả về một mảng JSON chứa các 'filePath' phù hợp. "
        "Ví dụ: [\"abcd/invoice.jpg\"]. "
        "Nếu không có file phù hợp trả về []. Không giải thích thêm."
    )

    response = _call_with_fallback(
        lambda model: client.responses.create(
            model=model,
            input=search_prompt,
            reasoning={"effort": "low"},
        )
    )

    raw = (response.output_text or "").strip()
    try:
        start = raw.find("[")
        end   = raw.rfind("]") + 1
        matched_paths = json.loads(raw[start:end]) if start != -1 and end > start else []
    except (json.JSONDecodeError, ValueError):
        matched_paths = []

    path_to_meta = {item["filePath"]: item for item in file_list}
    return [path_to_meta[p] for p in matched_paths if p in path_to_meta]


# ─────────────────────────────────────────────
# Legacy simple ask (OpenAI)
# ─────────────────────────────────────────────
def ask_gemini(prompt: str) -> str:
    """Legacy wrapper: gửi một prompt đơn giản tới GPT-5.5."""
    response = _call_with_fallback(
        lambda model: client.responses.create(
            model=model,
            input=prompt,
            reasoning={"effort": "low"},
        )
    )
    return response.output_text or ""
