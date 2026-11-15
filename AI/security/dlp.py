"""Scan locally before any external model upload. Unsupported types fail closed."""
import json
from pathlib import Path
from .policy import PolicyViolation, scan_text, guard_text

ocr_engine = None

def scan_file(path, ocr=None):
    ocr = ocr if ocr is not None else ocr_engine
    p = Path(path)
    if p.stat().st_size > 10 * 1024 * 1024:
        raise PolicyViolation("File vượt 10 MB")
    if p.suffix.lower() in {".jpg", ".jpeg", ".png", ".bmp", ".webp"}:
        if ocr is None:
            raise PolicyViolation("DLP OCR chưa sẵn sàng")
        from PIL import Image
        with Image.open(path) as image:
            if image.width * image.height > 20000000:
                raise PolicyViolation("Ảnh vượt giới hạn 20 megapixel")
        texts = []
        for result in ocr.predict(input=str(p)):
            data = result.json
            if isinstance(data, str):
                data = json.loads(data)
            data = data.get("res", data)
            texts.extend(data.get("rec_texts", []))
        text = "\n".join(texts)
    elif p.suffix.lower() in {".txt", ".md", ".csv", ".json", ".log", ".py", ".kt", ".java", ".sql"}:
        text = p.read_text(encoding="utf-8")
    else:
        raise PolicyViolation("Định dạng chưa được DLP hỗ trợ; chặn gửi ra AI bên ngoài")
    guard_text(text)
    return scan_text(text)
