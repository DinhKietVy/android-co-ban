from contextlib import asynccontextmanager
from io import BytesIO
import json

from fastapi import FastAPI, UploadFile, File, Form
from fastapi.responses import StreamingResponse
import os
from paddleocr import PaddleOCR
from PIL import Image, ImageDraw, ImageFont
import cv2
import numpy as np
import base64
import uuid
import tempfile

from ultralytics import YOLO

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


@app.post("/predict-image")
async def predict_image(username: str = Form(...), file: UploadFile = File(...)):
    global ocr

    if ocr is None:
        return {"error": "OCR chưa khởi tạo"}

    # ========================
    # 1. Save file
    # ========================
    temp_path = f"temp_{file.filename}"
    with open(temp_path, "wb") as f:
        f.write(await file.read())

    # ========================
    # 2. OCR
    # ========================
    results = ocr.predict(input=temp_path)

    json_path = f"result_{file.filename}.json"
    for res in results:
        res.save_to_json(json_path)

    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    # ========================
    # 3. Vẽ box và text lên ảnh
    # ========================
    image = cv2.imread(temp_path)
    
    # Chuyển ảnh sang RGB để dùng với Pillow
    image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    pil_img = Image.fromarray(image_rgb)
    draw = ImageDraw.Draw(pil_img)

    font_path = r"C:\Windows\Fonts\times.ttf"

    for text, poly in zip(data["rec_texts"], data["rec_polys"]):
        # Tính kích thước box để thiết lập cỡ chữ
        h1 = np.linalg.norm(np.array(poly[0]) - np.array(poly[3]))
        h2 = np.linalg.norm(np.array(poly[1]) - np.array(poly[2]))
        box_height = max(int((h1 + h2) / 2), 14)
        
        font_size = max(14, int(box_height * 0.8))
        font = ImageFont.truetype(font_path, font_size)

        # Vẽ polygon (box khoanh chữ)
        pts = [(p[0], p[1]) for p in poly]
        pts.append(pts[0])  # Nối điểm cuối với điểm đầu
        draw.line(pts, fill="red", width=max(2, int(box_height * 0.05)))
        
        # Lấy giới hạn tọa độ của box
        max_x = max([p[0] for p in poly])
        min_x = min([p[0] for p in poly])
        min_y = min([p[1] for p in poly])
        
        # Tính kích thước text trước khi vẽ
        temp_bbox = draw.textbbox((0, 0), text, font=font)
        text_w = temp_bbox[2] - temp_bbox[0]
        text_h = temp_bbox[3] - temp_bbox[1]
        
        # Thử đặt text bên phải box
        text_x = max_x + 5 
        text_y = min_y
        
        # Nếu text bị tràn mép phải của ảnh
        if text_x + text_w > pil_img.width:
            # Chuyển sang đặt bên trái box
            text_x = min_x - text_w - 5
            
            # Nếu bên trái cũng bị tràn thì đành đặt lên phía trên box
            if text_x < 0:
                text_x = min_x
                text_y = max(0, min_y - text_h - 5)
                
        # Vẽ background màu đỏ cho text để dễ nhìn trên mọi nền
        bbox = draw.textbbox((text_x, text_y), text, font=font)
        draw.rectangle(bbox, fill="red")
        
        # Vẽ chữ dự đoán
        draw.text((text_x, text_y), text, fill="white", font=font)

    # Chuyển ngược lại thành BGR cho OpenCV để trả về/lưu trữ
    image = cv2.cvtColor(np.array(pil_img), cv2.COLOR_RGB2BGR)

    # ========================
    # Save + return
    # ========================
    # Ensure user's AI directory exists in backend data using relative path
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    backend_data_dir = os.path.join(base_dir, "BackEnd", "data")
    user_ai_dir = os.path.join(backend_data_dir, username, ".AI")
    os.makedirs(user_ai_dir, exist_ok=True)
    
    output_path = os.path.join(user_ai_dir, f"fixed_{file.filename}")
    cv2.imwrite(output_path, image)

    # Convert ảnh sang chuỗi base64 để đính kèm trong JSON
    _, buffer = cv2.imencode('.jpg', image)
    image_base64 = base64.b64encode(buffer).decode('utf-8')

    return {
        "texts": data["rec_texts"],
        "image_base64": image_base64
    }

@app.post("/predict-yolo")
async def predict_yolo(username: str = Form(...), file: UploadFile = File(...)):
    global yolo_model
    if yolo_model is None:
        return {"error": "YOLO chưa khởi tạo"}

    # Đọc dữ liệu file trực tiếp vào bộ nhớ
    contents = await file.read()
    nparr = np.frombuffer(contents, np.uint8)
    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

    # Dự đoán
    results = yolo_model.predict(source=img, conf=0.25, save=False, show=False)
    
    # Lấy ảnh kết quả đã được vẽ bounding box
    res_img = results[0].plot()

    # Lưu ảnh vào thư mục AI của user bằng đường dẫn tương đối
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    backend_data_dir = os.path.join(base_dir, "BackEnd", "data")
    user_ai_dir = os.path.join(backend_data_dir, username, "AI")
    os.makedirs(user_ai_dir, exist_ok=True)
    
    output_path = os.path.join(user_ai_dir, f"yolo_{file.filename}")
    cv2.imwrite(output_path, res_img)

    # Encode ảnh thành định dạng jpg
    _, buffer = cv2.imencode('.jpg', res_img)
    
    # Trả về dưới dạng file stream (hiển thị luôn thành ảnh)
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
                    
                    # Lưu tạm ra json để đọc rec_texts như cách làm hiện tại
                    temp_json = os.path.join(tempfile.gettempdir(), f"{uuid.uuid4()}.json")
                    for res in results:
                        res.save_to_json(temp_json)
                        
                    if os.path.exists(temp_json):
                        with open(temp_json, "r", encoding="utf-8") as f:
                            data = json.load(f)
                        os.remove(temp_json)
                        
                        rec_texts = data.get("rec_texts", [])
                        
                        # Kiểm tra xem có text nào chứa search_string không (không phân biệt hoa thường)
                        if any(search_string.lower() in text.lower() for text in rec_texts):
                            # Đọc ảnh và chuyển sang base64
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