from ultralytics import YOLO

# Load model
model = YOLO(r"D:\Code\Android\AI\yolo26\best.pt")  # model custom của bạn

# Predict và tự động lưu ảnh có bounding box
results = model.predict(
    source=r"D:\Code\Android\AI\yolo26\3.jpg",  # ảnh input
    save=True,        # lưu ảnh kết quả có khung
    show=False,       # không cần mở cửa sổ preview
    conf=0.25         # ngưỡng confidence (có thể chỉnh)
)

print("Ảnh đã được lưu trong thư mục runs/detect/predict/")