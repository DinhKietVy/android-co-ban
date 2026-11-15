# Bảo mật AI

## Cấu hình và khởi động

- Đặt cùng `AI_INTERNAL_SECRET` ngẫu nhiên (ít nhất 32 ký tự) trong môi trường backend và AI. Không nhúng khóa vào Android hoặc Git.
- Backend: `AI_INTERNAL_URL=http://127.0.0.1:8000`. Khi chạy khác máy bắt buộc HTTPS với chứng chỉ được tin cậy; không tắt xác thực TLS.
- Chạy AI từ thư mục `AI`: `uvicorn app:app --host 127.0.0.1 --port 8000 --workers 1`. Dùng một worker vì bộ nhớ chống phát lại nằm trong tiến trình; khi mở rộng cần nonce store dùng chung (Redis SET NX + TTL).
- AI cần các thư viện hiện có: FastAPI, uvicorn, python-multipart, PaddleOCR/PaddlePaddle phù hợp GPU, Pillow, OpenCV, numpy, ultralytics, openai, python-dotenv, pyodbc. Dùng môi trường và model PaddleOCR hiện có của dự án.
- SQL Server của chatbot dùng Encrypt=yes, TrustServerCertificate=no: cần cấu hình chứng chỉ SQL Server hợp lệ trước khi chạy.
- Android dùng `API_BASE_URL` và CookieJar đăng nhập chung, gọi qua `/ai/`. Không công khai cổng AI qua ngrok; chỉ gateway được truy cập mạng nội bộ.

## Hành vi

Gateway kiểm tra cookie đăng nhập, lấy username từ DB theo user ID và ký HMAC-SHA256 trên method, đường dẫn, timestamp, nonce, danh tính, SHA256 body. AI từ chối chữ ký sai, payload sửa đổi, timestamp quá 60 giây và nonce lặp lại; giới hạn body 12 MB. Các API session kiểm tra quyền sở hữu, multipart kiểm tra username đã ký. Gateway không chuyển cookie hay khóa đăng nhập sang AI.

`POST /ai/api/ai/dlp/scan`: multipart `file`, trả `sensitive`, `findings` (loại và vị trí), `redactedText`. PaddleOCR quét JPG/JPEG/PNG/BMP/WebP, văn bản UTF-8 hỗ trợ TXT/MD/CSV/JSON/LOG/PY/KT/JAVA/SQL. Giới hạn 10 MB và 20 megapixel. Phát hiện email, điện thoại Việt Nam, chuỗi 12 số, chuỗi 16 số và secret dạng key=value. Không trả nguyên giá trị trong findings. Kết quả `/predict-image` cũng có trường `dlp`; ảnh OCR trả về vẫn là dữ liệu riêng của người dùng, không phải ảnh đã che.

Trước khi gửi tệp tới nhà cung cấp AI, chạy DLP cục bộ và chặn tệp nhạy cảm. Định dạng chưa hỗ trợ (kể cả PDF/DOCX/XLSX) bị chặn, không tự động gửi bản gốc ra ngoài. Prompt, lịch sử, metadata và phản hồi được kiểm tra/che dữ liệu theo mẫu. Lịch sử client không được dùng thay lịch sử DB.

Guardrails chuẩn hóa Unicode và phát hiện một số mẫu Prompt Injection tiếng Việt/Anh, bổ sung chỉ dẫn dữ liệu không đáng tin. Chatbot chỉ được gọi `list_files`; tạo/xóa/di chuyển qua chatbot bị vô hiệu hóa ở cả khai báo tools lẫn bộ thực thi. Người dùng vẫn thao tác bằng API quản lý tệp thông thường. Kiểm tra đường dẫn dùng resolve + containment thay cho startswith.

## Giới hạn và kiểm thử

Regex và OCR có thể bỏ sót hoặc báo nhầm; đây là lớp giảm rủi ro, không bảo đảm chặn mọi Prompt Injection/DLP. Không che trực tiếp ảnh và chưa hỗ trợ OCR PDF. Không thay thế phân quyền API quản lý tệp hiện có. Cần kiểm thử tích hợp GPU/model thực, SQL Server, TLS và Android sau khi cấu hình môi trường.

Chạy `python -B -m unittest discover -s AI/tests -v` từ gốc dự án; `BackEnd/node_modules/.bin/tsc --noEmit -p BackEnd/tsconfig.json` để kiểm tra TypeScript.
