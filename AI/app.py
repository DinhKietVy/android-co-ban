from contextlib import asynccontextmanager
from io import BytesIO
import json
import sys

from fastapi import FastAPI, UploadFile, File, Form, Body
from fastapi.responses import StreamingResponse, JSONResponse
import os
from paddleocr import PaddleOCR
from PIL import Image, ImageDraw, ImageFont
import cv2
import numpy as np
import base64
import uuid
import tempfile
from typing import Optional, List

from ultralytics import YOLO

# Thêm thư mục chatbot vào sys.path để import openai_service
_chatbot_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "chatbot")
if _chatbot_dir not in sys.path:
    sys.path.insert(0, _chatbot_dir)

from openai_service import chat_with_gemini, semantic_search, get_db_connection

ocr = None
yolo_model = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global ocr, yolo_model

    print("Đang load OCR model...")

    ocr = PaddleOCR(
        paddlex_config=r"D:\Code\Android\AI\paddle\25_2_mobile\PaddleOCR.yaml",
        use_doc_orientation_classify=False,
        use_doc_unwarping=False,
        use_textline_orientation=False,
        lang="vi",

        text_recognition_model_name="PP-OCRv5_mobile_rec",
        text_recognition_model_dir=r"D:\Code\Android\AI\paddle\25_2_mobile\best_acc",
        device="gpu"
    )

    print("OCR model đã sẵn sàng!")

    print("Đang load YOLO model...")
    yolo_model = YOLO(r"D:\Code\Android\AI\yolo26\best.pt")
    print("YOLO model đã sẵn sàng!")

    yield

    print("Server đang tắt...")


app = FastAPI(lifespan=lifespan)


# ============================================================
# OCR / YOLO ENDPOINTS
# ============================================================

@app.post("/predict-image")
async def predict_image(username: str = Form(...), file: UploadFile = File(...)):
    global ocr, yolo_model

    if ocr is None:
        return {"error": "OCR chưa khởi tạo"}
    if yolo_model is None:
        return {"error": "YOLO chưa khởi tạo"}

    # ── 1. Save file and read to memory ──
    contents = await file.read()
    temp_path = f"temp_{file.filename}"
    with open(temp_path, "wb") as f:
        f.write(contents)

    nparr = np.frombuffer(contents, np.uint8)
    img_bgr = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

    # ── 2. YOLO (Object Detection) ──
    yolo_results = yolo_model.predict(source=img_bgr, conf=0.25, save=False, show=False)

    detected_classes = []
    for box in yolo_results[0].boxes:
        cls_id = int(box.cls[0].item())
        cls_name = yolo_model.names[cls_id]
        detected_classes.append(cls_name)
    detected_classes = list(set(detected_classes))

    res_img_bgr = yolo_results[0].plot()

    # ── 3. OCR (Text Recognition) ──
    ocr_results = ocr.predict(input=temp_path)

    json_path = f"result_{file.filename}.json"
    for res in ocr_results:
        res.save_to_json(json_path)

    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    # ── 4. Vẽ box và text OCR lên ảnh YOLO ──
    image_rgb = cv2.cvtColor(res_img_bgr, cv2.COLOR_BGR2RGB)
    pil_img = Image.fromarray(image_rgb)
    draw = ImageDraw.Draw(pil_img)

    font_path = r"C:\Windows\Fonts\times.ttf"

    for text, poly in zip(data.get("rec_texts", []), data.get("rec_polys", [])):
        h1 = np.linalg.norm(np.array(poly[0]) - np.array(poly[3]))
        h2 = np.linalg.norm(np.array(poly[1]) - np.array(poly[2]))
        box_height = max(int((h1 + h2) / 2), 14)

        font_size = max(14, int(box_height * 0.8))
        font = ImageFont.truetype(font_path, font_size)

        pts = [(p[0], p[1]) for p in poly]
        pts.append(pts[0])
        draw.line(pts, fill="red", width=max(2, int(box_height * 0.05)))

        max_x = max([p[0] for p in poly])
        min_x = min([p[0] for p in poly])
        min_y = min([p[1] for p in poly])

        temp_bbox = draw.textbbox((0, 0), text, font=font)
        text_w = temp_bbox[2] - temp_bbox[0]
        text_h = temp_bbox[3] - temp_bbox[1]

        text_x = max_x + 5
        text_y = min_y

        if text_x + text_w > pil_img.width:
            text_x = min_x - text_w - 5
            if text_x < 0:
                text_x = min_x
                text_y = max(0, min_y - text_h - 5)

        bbox = draw.textbbox((text_x, text_y), text, font=font)
        draw.rectangle(bbox, fill="red")
        draw.text((text_x, text_y), text, fill="white", font=font)

    image = cv2.cvtColor(np.array(pil_img), cv2.COLOR_RGB2BGR)

    # ── Save + return ──
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    backend_data_dir = os.path.join(base_dir, "BackEnd", "data")
    user_ai_dir = os.path.join(backend_data_dir, username, ".AI")
    os.makedirs(user_ai_dir, exist_ok=True)

    output_path = os.path.join(user_ai_dir, f"fixed_{file.filename}")
    cv2.imwrite(output_path, image)

    _, buffer = cv2.imencode('.jpg', image)
    image_base64 = base64.b64encode(buffer).decode('utf-8')

    return {
        "texts": data.get("rec_texts", []),
        "tags": detected_classes,
        "image_base64": image_base64
    }


@app.post("/predict-yolo")
async def predict_yolo(username: str = Form(...), file: UploadFile = File(...)):
    global yolo_model
    if yolo_model is None:
        return {"error": "YOLO chưa khởi tạo"}

    contents = await file.read()
    nparr = np.frombuffer(contents, np.uint8)
    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

    results = yolo_model.predict(source=img, conf=0.25, save=False, show=False)
    res_img = results[0].plot()

    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    backend_data_dir = os.path.join(base_dir, "BackEnd", "data")
    user_ai_dir = os.path.join(backend_data_dir, username, "AI")
    os.makedirs(user_ai_dir, exist_ok=True)

    output_path = os.path.join(user_ai_dir, f"yolo_{file.filename}")
    cv2.imwrite(output_path, res_img)

    _, buffer = cv2.imencode('.jpg', res_img)

    return StreamingResponse(BytesIO(buffer.tobytes()), media_type="image/jpeg")


@app.post("/search-images-by-text")
async def search_images_by_text(username: str = Form(...), search_string: str = Form(...)):
    global ocr
    if ocr is None:
        return {"error": "OCR chưa khởi tạo"}

    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    user_data_dir = os.path.join(base_dir, "BackEnd", "data", username)

    if not os.path.exists(user_data_dir):
        return {"error": "Thư mục user không tồn tại"}

    matched_images = []

    for root, dirs, files in os.walk(user_data_dir):
        for file in files:
            if file.lower().endswith(('.png', '.jpg', '.jpeg', '.bmp')):
                img_path = os.path.join(root, file)

                try:
                    results = ocr.predict(input=img_path)

                    temp_json = os.path.join(tempfile.gettempdir(), f"{uuid.uuid4()}.json")
                    for res in results:
                        res.save_to_json(temp_json)

                    if os.path.exists(temp_json):
                        with open(temp_json, "r", encoding="utf-8") as f:
                            data = json.load(f)
                        os.remove(temp_json)

                        rec_texts = data.get("rec_texts", [])

                        if any(search_string.lower() in text.lower() for text in rec_texts):
                            img = cv2.imread(img_path)
                            if img is not None:
                                _, buffer = cv2.imencode('.jpg', img)
                                image_base64 = base64.b64encode(buffer).decode('utf-8')
                                matched_images.append({
                                    "filename": file,
                                    "path": os.path.relpath(img_path, user_data_dir),
                                    "image_base64": image_base64
                                })
                except Exception as e:
                    print(f"Lỗi khi xử lý ảnh {img_path}: {e}")
                    continue

    return {
        "search_string": search_string,
        "total_matched": len(matched_images),
        "matched_images": matched_images
    }


# ============================================================
# PYDANTIC MODELS
# ============================================================

from pydantic import BaseModel
from fastapi.responses import JSONResponse


class HistoryPart(BaseModel):
    text: str


class HistoryTurn(BaseModel):
    role: str
    parts: List[HistoryPart]


class ChatRequest(BaseModel):
    username: str
    prompt: str
    filePath: Optional[str] = None
    # Nếu truyền sessionId thì lưu vào session đó, không truyền thì tạo session mới
    sessionId: Optional[int] = None
    # history vẫn giữ để backward-compatible, nhưng ưu tiên load từ DB nếu có sessionId
    history: Optional[List[HistoryTurn]] = None


class CreateSessionRequest(BaseModel):
    username: str
    title: Optional[str] = None
    aiService: Optional[str] = "openai"


class SearchRequest(BaseModel):
    username: str
    query: str


# ============================================================
# DB HELPERS (chat sessions & messages)
# ============================================================

def _get_user_id(conn, username: str) -> int | None:
    """Lấy id của user từ bảng users theo username."""
    cursor = conn.cursor()
    cursor.execute("SELECT id FROM users WHERE username = ?", (username,))
    row = cursor.fetchone()
    return row[0] if row else None


def _create_session(conn, user_id: int, title: str, ai_service: str) -> int:
    """Tạo chat session mới, trả về session id."""
    cursor = conn.cursor()
    cursor.execute(
        """
        INSERT INTO chat_sessions (user_id, title, ai_service)
        OUTPUT INSERTED.id
        VALUES (?, ?, ?)
        """,
        (user_id, title, ai_service),
    )
    session_id = cursor.fetchone()[0]
    conn.commit()
    return session_id


def _save_message(
    conn,
    session_id: int,
    role: str,
    content: str,
    file_path: str | None = None,
    actions_executed: list | None = None,
    affected_items: list | None = None,
) -> int:
    """Lưu một message vào bảng chat_messages, trả về message id."""
    cursor = conn.cursor()
    cursor.execute(
        """
        INSERT INTO chat_messages
            (session_id, role, content, file_path, actions_executed, affected_items)
        OUTPUT INSERTED.id
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (
            session_id,
            role,
            content,
            file_path,
            json.dumps(actions_executed, ensure_ascii=False) if actions_executed else None,
            json.dumps(affected_items,   ensure_ascii=False) if affected_items   else None,
        ),
    )
    msg_id = cursor.fetchone()[0]
    # Cập nhật updated_at của session
    cursor.execute(
        "UPDATE chat_sessions SET updated_at = GETDATE() WHERE id = ?",
        (session_id,),
    )
    conn.commit()
    return msg_id


def _load_history_from_db(conn, session_id: int) -> list[dict]:
    """
    Đọc toàn bộ message của session từ DB, trả về đúng format
    history mà chat_with_gemini() cần:
    [{"role": "user"|"model", "parts": [{"text": "..."}]}]
    """
    cursor = conn.cursor()
    cursor.execute(
        """
        SELECT role, content
        FROM chat_messages
        WHERE session_id = ?
        ORDER BY id ASC
        """,
        (session_id,),
    )
    rows = cursor.fetchall()
    return [
        {"role": row[0], "parts": [{"text": row[1]}]}
        for row in rows
    ]


# ============================================================
# AI CHATBOT
# POST /api/ai/chat
# ============================================================

@app.post("/api/ai/chat")
async def ai_chat(req: ChatRequest):
    """
    Chatbot OpenAI GPT-5.5.

    - Nếu truyền sessionId: load history từ DB, lưu message mới vào session đó.
    - Nếu không truyền sessionId: tạo session mới, lưu toàn bộ vào DB.
    - Trả về sessionId để client lưu lại cho các lượt sau.
    """
    if not req.username or not req.prompt:
        return JSONResponse(
            status_code=400,
            content={"success": False, "error": "Thiếu username hoặc prompt"},
        )

    try:
        conn = get_db_connection()
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={"success": False, "error": f"Không kết nối được DB: {str(e)}"},
        )

    try:
        user_id = _get_user_id(conn, req.username)
        if user_id is None:
            return JSONResponse(
                status_code=404,
                content={"success": False, "error": "Không tìm thấy user"},
            )

        # ── Xác định session ──
        session_id = req.sessionId

        if session_id is not None:
            # Kiểm tra session tồn tại và thuộc về đúng user
            cursor = conn.cursor()
            cursor.execute(
                "SELECT id FROM chat_sessions WHERE id = ? AND user_id = ?",
                (session_id, user_id),
            )
            if cursor.fetchone() is None:
                return JSONResponse(
                    status_code=404,
                    content={"success": False, "error": f"Session {session_id} không tồn tại hoặc không thuộc user này"},
                )
        else:
            # Tạo session mới, title để NULL — sẽ được đặt từ prompt đầu tiên
            session_id = _create_session(conn, user_id, None, "openai")

        # ── Load history từ DB (ưu tiên DB, fallback sang request.history) ──
        history_from_db = _load_history_from_db(conn, session_id)

        if history_from_db:
            history_raw = history_from_db
        elif req.history:
            history_raw = [
                {
                    "role": turn.role,
                    "parts": [{"text": p.text} for p in turn.parts],
                }
                for turn in req.history
            ]
        else:
            history_raw = None

        # ── Lưu message của user ──
        # Nếu đây là message đầu tiên trong session (title còn NULL) → đặt title
        cursor = conn.cursor()
        cursor.execute(
            "SELECT COUNT(*) FROM chat_messages WHERE session_id = ?",
            (session_id,),
        )
        is_first_message = cursor.fetchone()[0] == 0

        _save_message(
            conn,
            session_id=session_id,
            role="user",
            content=req.prompt,
            file_path=req.filePath if req.filePath else None,
        )

        if is_first_message:
            # Cắt lấy tối đa 80 ký tự, bỏ xuống dòng
            auto_title = req.prompt.replace("\n", " ").strip()[:80]
            cursor.execute(
                "UPDATE chat_sessions SET title = ? WHERE id = ? AND title IS NULL",
                (auto_title, session_id),
            )
            conn.commit()

        # ── Gọi AI ──
        result = chat_with_gemini(
            username=req.username,
            prompt=req.prompt,
            history=history_raw,
            file_path=req.filePath,
        )

        # ── Lưu response của AI ──
        _save_message(
            conn,
            session_id=session_id,
            role="model",
            content=result.get("text", ""),
            actions_executed=result.get("actionsExecuted"),
            affected_items=result.get("affectedItems"),
        )

        return {
            "success": True,
            "sessionId": session_id,
            "data": result,
        }

    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={"success": False, "error": f"Lỗi xử lý chatbot: {str(e)}"},
        )
    finally:
        conn.close()


# ============================================================
# CHAT SESSION MANAGEMENT
# ============================================================

@app.post("/api/ai/sessions")
async def create_session(req: CreateSessionRequest):
    """
    Tạo một chat session mới cho user.

    Body:
        username  (str, required)
        title     (str, optional) — hiển thị trong sidebar
        aiService (str, optional) — 'openai' | 'gemini', mặc định 'openai'
    """
    if not req.username:
        return JSONResponse(
            status_code=400,
            content={"success": False, "error": "Thiếu username"},
        )

    try:
        conn = get_db_connection()
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={"success": False, "error": f"Không kết nối được DB: {str(e)}"},
        )

    try:
        user_id = _get_user_id(conn, req.username)
        if user_id is None:
            return JSONResponse(
                status_code=404,
                content={"success": False, "error": "Không tìm thấy user"},
            )

        session_id = _create_session(
            conn,
            user_id,
            req.title or None,   # NULL nếu không truyền, sẽ tự đặt khi chat đầu tiên
            req.aiService or "openai",
        )

        return {
            "success": True,
            "data": {"sessionId": session_id},
        }

    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={"success": False, "error": str(e)},
        )
    finally:
        conn.close()


@app.get("/api/ai/sessions/{username}")
async def get_sessions(username: str):
    """
    Lấy danh sách tất cả chat session của user, sắp xếp mới nhất trước.

    Response:
        [
          {
            "id": 1,
            "title": "...",
            "aiService": "openai",
            "createdAt": "...",
            "updatedAt": "..."
          },
          ...
        ]
    """
    try:
        conn = get_db_connection()
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={"success": False, "error": f"Không kết nối được DB: {str(e)}"},
        )

    try:
        user_id = _get_user_id(conn, username)
        if user_id is None:
            return JSONResponse(
                status_code=404,
                content={"success": False, "error": "Không tìm thấy user"},
            )

        cursor = conn.cursor()
        cursor.execute(
            """
            SELECT id, title, ai_service, created_at, updated_at
            FROM chat_sessions
            WHERE user_id = ?
            ORDER BY updated_at DESC
            """,
            (user_id,),
        )
        rows = cursor.fetchall()

        sessions = [
            {
                "id":         row[0],
                "title":      row[1],
                "aiService":  row[2],
                "createdAt":  str(row[3]),
                "updatedAt":  str(row[4]),
            }
            for row in rows
        ]

        return {"success": True, "data": sessions}

    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={"success": False, "error": str(e)},
        )
    finally:
        conn.close()


@app.get("/api/ai/sessions/{session_id}/messages")
async def get_session_messages(session_id: int):
    """
    Lấy toàn bộ message của một session theo thứ tự thời gian.

    Response:
        [
          {
            "id": 1,
            "role": "user",
            "content": "...",
            "filePath": null,
            "actionsExecuted": null,
            "affectedItems": null,
            "createdAt": "..."
          },
          ...
        ]
    """
    try:
        conn = get_db_connection()
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={"success": False, "error": f"Không kết nối được DB: {str(e)}"},
        )

    try:
        cursor = conn.cursor()

        # Kiểm tra session tồn tại
        cursor.execute("SELECT id FROM chat_sessions WHERE id = ?", (session_id,))
        if cursor.fetchone() is None:
            return JSONResponse(
                status_code=404,
                content={"success": False, "error": "Session không tồn tại"},
            )

        cursor.execute(
            """
            SELECT id, role, content, file_path,
                   actions_executed, affected_items, created_at
            FROM chat_messages
            WHERE session_id = ?
            ORDER BY id ASC
            """,
            (session_id,),
        )
        rows = cursor.fetchall()

        def _parse_json_col(val):
            if val is None:
                return None
            try:
                return json.loads(val)
            except (json.JSONDecodeError, TypeError):
                return val

        messages = [
            {
                "id":              row[0],
                "role":            row[1],
                "content":         row[2],
                "filePath":        row[3],
                "actionsExecuted": _parse_json_col(row[4]),
                "affectedItems":   _parse_json_col(row[5]),
                "createdAt":       str(row[6]),
            }
            for row in rows
        ]

        return {"success": True, "data": messages}

    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={"success": False, "error": str(e)},
        )
    finally:
        conn.close()


@app.delete("/api/ai/sessions/{session_id}")
async def delete_session(session_id: int):
    """
    Xóa một chat session và toàn bộ message của nó (ON DELETE CASCADE).

    Response:
        { "success": true }
    """
    try:
        conn = get_db_connection()
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={"success": False, "error": f"Không kết nối được DB: {str(e)}"},
        )

    try:
        cursor = conn.cursor()
        cursor.execute("SELECT id FROM chat_sessions WHERE id = ?", (session_id,))
        if cursor.fetchone() is None:
            return JSONResponse(
                status_code=404,
                content={"success": False, "error": "Session không tồn tại"},
            )

        cursor.execute("DELETE FROM chat_sessions WHERE id = ?", (session_id,))
        conn.commit()

        return {"success": True}

    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={"success": False, "error": str(e)},
        )
    finally:
        conn.close()


@app.patch("/api/ai/sessions/{session_id}/title")
async def rename_session(session_id: int, body: dict = Body(...)):
    """
    Đổi tên title của session.

    Body: { "title": "Tên mới" }
    """
    title = (body.get("title") or "").strip()
    if not title:
        return JSONResponse(
            status_code=400,
            content={"success": False, "error": "Thiếu title"},
        )

    try:
        conn = get_db_connection()
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={"success": False, "error": f"Không kết nối được DB: {str(e)}"},
        )

    try:
        cursor = conn.cursor()
        cursor.execute("SELECT id FROM chat_sessions WHERE id = ?", (session_id,))
        if cursor.fetchone() is None:
            return JSONResponse(
                status_code=404,
                content={"success": False, "error": "Session không tồn tại"},
            )

        cursor.execute(
            "UPDATE chat_sessions SET title = ?, updated_at = GETDATE() WHERE id = ?",
            (title, session_id),
        )
        conn.commit()

        return {"success": True}

    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={"success": False, "error": str(e)},
        )
    finally:
        conn.close()


# ============================================================
# AI SEARCH
# POST /api/ai/search
# ============================================================

@app.post("/api/ai/search")
async def ai_search(req: SearchRequest):
    """
    Tìm kiếm file bằng ngôn ngữ tự nhiên.
    Ví dụ: "Tìm cho tôi mấy cái hóa đơn tuần trước"
    """
    if not req.username or not req.query:
        return JSONResponse(
            status_code=400,
            content={"success": False, "error": "Thiếu username hoặc query"},
        )

    try:
        matched_files = semantic_search(
            username=req.username,
            query=req.query,
        )

        return {
            "success": True,
            "data": {
                "query":        req.query,
                "totalMatched": len(matched_files),
                "files":        matched_files,
            },
        }

    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={"success": False, "error": f"Lỗi tìm kiếm: {str(e)}"},
        )
