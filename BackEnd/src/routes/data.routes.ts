import { Router } from 'express';
import {convertFile, uploadData, updateFileContent, createFolder, moveFile, moveFolder, deleteFile, deleteFolder, listDirectory, downloadFile, renameFile, renameFolder, searchFiles, compressFiles, extractFile, shareFile, unshareFile, updateShare, getSharedByOwner, getSharedToMe } from '../controllers/data.controller';
import { authenticateToken } from '../middleware/auth';


const router = Router();

/**
 * @swagger
 * /api/data/upload:
 *   post:
 *     summary: Upload file dữ liệu
 *     description: Tải lên một file vào thư mục của người dùng. Bạn có thể chỉ định thư mục đích cụ thể thông qua trường `targetPath`.
 *     tags: [Data]
 *     requestBody:
 *       required: true
 *       content:
 *         multipart/form-data:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - file
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               targetPath:
 *                 type: string
 *                 description: Đường dẫn thư mục đích nằm trong thư mục gốc của user (ví dụ 'hinhanh/2026'). Để trống nếu muốn upload trực tiếp vào thư mục gốc.
 *                 example: hinhanh/2026
 *               file:
 *                 type: string
 *                 format: binary
 *                 description: File cần upload
 *     responses:
 *       200:
 *         description: Upload file thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Upload dữ liệu thành công
 *                 data:
 *                   type: object
 *       400:
 *         description: Dữ liệu không hợp lệ (thiếu field, sai đường dẫn, hoặc file bị lỗi)
 *       500:
 *         description: Lỗi hệ thống server
 */
router.post('/upload', authenticateToken, uploadData);

// API để ghi đè nội dung chữ vào file cũ
router.put('/file-content', authenticateToken, updateFileContent);

/**
 * @swagger
 * /api/data/folder:
 *   post:
 *     summary: Tạo thư mục mới
 *     description: Tạo một thư mục con nằm bên trong thư mục của người dùng. Yêu cầu thư mục cha (`targetPath`) phải tồn tại.
 *     tags: [Data]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - targetPath
 *               - folderName
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               targetPath:
 *                 type: string
 *                 description: Đường dẫn của thư mục cha. Truyền chuỗi rỗng `""` nếu muốn tạo ngay tại thư mục gốc của user.
 *                 example: hinhanh/2026
 *               folderName:
 *                 type: string
 *                 description: Tên thư mục mới sẽ được tạo
 *                 example: avatar
 *     responses:
 *       201:
 *         description: Tạo thư mục thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Tạo thư mục thành công
 *                 data:
 *                   type: object
 *       400:
 *         description: Dữ liệu không hợp lệ hoặc thư mục cùng tên đã tồn tại
 *       403:
 *         description: Đường dẫn không hợp lệ (bị lỗi Path Traversal)
 *       404:
 *         description: Đường dẫn thư mục cha không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 */
router.post('/folder', authenticateToken, createFolder);

/**
 * @swagger
 * /api/data/move-file:
 *   post:
 *     summary: Di chuyển file
 *     description: Đổi vị trí lưu trữ của một file từ thư mục này sang một thư mục khác bên trong khu vực dữ liệu của người dùng.
 *     tags: [Data]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - sourceFilePath
 *               - targetFolderPath
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               sourceFilePath:
 *                 type: string
 *                 description: Đường dẫn hiện tại của file cần chuyển (tính từ thư mục gốc của user)
 *                 example: hinhanh/2026/avatar.png
 *               targetFolderPath:
 *                 type: string
 *                 description: Đường dẫn thư mục đích muốn chuyển file tới. Truyền chuỗi rỗng `""` nếu muốn chuyển ra ngoài cùng.
 *                 example: hinhanh/2027
 *     responses:
 *       200:
 *         description: Di chuyển file thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Di chuyển file thành công
 *                 data:
 *                   type: object
 *       400:
 *         description: Thiếu dữ liệu hoặc file cùng tên đã tồn tại ở thư mục đích
 *       403:
 *         description: Đường dẫn không hợp lệ (bị lỗi Path Traversal)
 *       404:
 *         description: File nguồn hoặc thư mục đích không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 */
router.post('/move-file', authenticateToken, moveFile);

/**
 * @swagger
 * /api/data/move-folder:
 *   post:
 *     summary: Di chuyển thư mục
 *     description: Đổi vị trí lưu trữ của một thư mục từ vị trí này sang một vị trí khác bên trong khu vực dữ liệu của người dùng.
 *     tags: [Data]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - sourceFolderPath
 *               - targetFolderPath
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               sourceFolderPath:
 *                 type: string
 *                 description: Đường dẫn hiện tại của thư mục cần di chuyển (tính từ thư mục gốc của user)
 *                 example: hinhanh/2026
 *               targetFolderPath:
 *                 type: string
 *                 description: Đường dẫn thư mục đích muốn di chuyển thư mục tới. Truyền chuỗi rỗng `""` nếu muốn chuyển ra ngoài cùng.
 *                 example: quan_ly_du_lieu/năm_2026
 *     responses:
 *       200:
 *         description: Di chuyển thư mục thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Di chuyển thư mục thành công
 *                 data:
 *                   type: object
 *       400:
 *         description: Thiếu dữ liệu, không thể di chuyển thư mục gốc, hoặc thư mục có thể gây ra vòng lặp (di chuyển vào chính nó)
 *       403:
 *         description: Đường dẫn không hợp lệ (bị lỗi Path Traversal)
 *       404:
 *         description: Thư mục nguồn hoặc thư mục đích không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 */
router.post('/move-folder', authenticateToken, moveFolder);

/**
 * @swagger
 * /api/data/file:
 *   delete:
 *     summary: Xoá file
 *     description: Xoá vĩnh viễn một file nằm trong khu vực dữ liệu của người dùng.
 *     tags: [Data]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - filePath
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               filePath:
 *                 type: string
 *                 description: Đường dẫn của file cần xoá (tính từ thư mục gốc của user)
 *                 example: hinhanh/2026/avatar.png
 *     responses:
 *       200:
 *         description: Xoá file thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Xoá file thành công
 *                 data:
 *                   type: object
 *       400:
 *         description: Đường dẫn trỏ tới thư mục thay vì file hoặc thiếu dữ liệu
 *       403:
 *         description: Đường dẫn không hợp lệ (bị lỗi Path Traversal)
 *       404:
 *         description: File không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 */
router.delete('/file', authenticateToken, deleteFile);

/**
 * @swagger
 * /api/data/folder:
 *   delete:
 *     summary: Xoá thư mục (xoá đệ quy)
 *     description: "Xoá vĩnh viễn một thư mục cùng toàn bộ các thư mục con và file bên trong nó. Chú ý: Hành động này không thể hoàn tác."
 *     tags: [Data]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - folderPath
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               folderPath:
 *                 type: string
 *                 description: Đường dẫn của thư mục cần xoá (tính từ thư mục gốc của user)
 *                 example: hinhanh/2026
 *     responses:
 *       200:
 *         description: Xoá thư mục thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Xoá thư mục thành công
 *                 data:
 *                   type: object
 *       400:
 *         description: Đường dẫn trỏ tới file thay vì thư mục, thiếu dữ liệu, hoặc cố tình xoá thư mục gốc
 *       403:
 *         description: Đường dẫn không hợp lệ (bị lỗi Path Traversal)
 *       404:
 *         description: Thư mục không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 */
router.delete('/folder', authenticateToken, deleteFolder);

/**
 * @swagger
 * /api/data/list:
 *   post:
 *     summary: Lấy danh sách file và thư mục
 *     description: Lấy toàn bộ danh sách các file và thư mục con nằm bên trong thư mục hiện tại được chỉ định.
 *     tags: [Data]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - folderPath
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               folderPath:
 *                 type: string
 *                 description: Đường dẫn của thư mục muốn lấy dữ liệu. Truyền chuỗi rỗng `""` nếu muốn lấy danh sách ở ngoài cùng.
 *                 example: hinhanh
 *     responses:
 *       200:
 *         description: Lấy dữ liệu thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Lấy dữ liệu thành công
 *                 data:
 *                   type: object
 *       400:
 *         description: Đường dẫn trỏ tới file thay vì thư mục, hoặc thiếu dữ liệu
 *       403:
 *         description: Đường dẫn không hợp lệ (bị lỗi Path Traversal)
 *       404:
 *         description: Thư mục không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 */
router.post('/list', authenticateToken, listDirectory);

/**
 * @swagger
 * /api/data/download:
 *   get:
 *     summary: Tải file về máy (GET)
 *     description: Tải một file từ server về máy người dùng. Hỗ trợ truyền tham số qua query.
 *     tags: [Data]
 *     parameters:
 *       - in: query
 *         name: username
 *         schema:
 *           type: string
 *         required: true
 *         description: Tên đăng nhập của người dùng
 *         example: admin123
 *       - in: query
 *         name: filePath
 *         schema:
 *           type: string
 *         required: true
 *         description: Đường dẫn của file cần tải (tính từ thư mục gốc của user)
 *         example: hinhanh/2026/avatar.png
 *     responses:
 *       200:
 *         description: Trả về file để tải xuống
 *         content:
 *           application/octet-stream:
 *             schema:
 *               type: string
 *               format: binary
 *       400:
 *         description: Đường dẫn trỏ tới thư mục thay vì file, hoặc thiếu dữ liệu
 *       403:
 *         description: Đường dẫn không hợp lệ (bị lỗi Path Traversal)
 *       404:
 *         description: File không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 *   post:
 *     summary: Tải file về máy (POST)
 *     description: Tải một file từ server về máy người dùng. Hỗ trợ truyền tham số qua body.
 *     tags: [Data]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - filePath
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               filePath:
 *                 type: string
 *                 description: Đường dẫn của file cần tải (tính từ thư mục gốc của user)
 *                 example: hinhanh/2026/avatar.png
 *     responses:
 *       200:
 *         description: Trả về file để tải xuống
 *         content:
 *           application/octet-stream:
 *             schema:
 *               type: string
 *               format: binary
 *       400:
 *         description: Đường dẫn trỏ tới thư mục thay vì file, hoặc thiếu dữ liệu
 *       403:
 *         description: Đường dẫn không hợp lệ (bị lỗi Path Traversal)
 *       404:
 *         description: File không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 */
router.get('/download', authenticateToken, downloadFile);
router.post('/download', authenticateToken, downloadFile);

/**
 * @swagger
 * /api/data/rename-file:
 *   post:
 *     summary: Đổi tên file
 *     description: Đổi tên một file hiện có trong khu vực dữ liệu của người dùng.
 *     tags: [Data]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - filePath
 *               - newFileName
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               filePath:
 *                 type: string
 *                 description: Đường dẫn của file cần đổi tên (tính từ thư mục gốc của user)
 *                 example: hinhanh/2026/avatar.png
 *               newFileName:
 *                 type: string
 *                 description: Tên mới cho file (không bao gồm đường dẫn)
 *                 example: avatar_new.png
 *     responses:
 *       200:
 *         description: Đổi tên file thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Đổi tên file thành công
 *                 data:
 *                   type: object
 *       400:
 *         description: Dữ liệu không hợp lệ, đường dẫn trỏ tới thư mục, hoặc tên mới đã tồn tại
 *       403:
 *         description: Đường dẫn không hợp lệ (bị lỗi Path Traversal)
 *       404:
 *         description: File không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 */
router.post('/rename-file', authenticateToken, renameFile);

/**
 * @swagger
 * /api/data/rename-folder:
 *   post:
 *     summary: Đổi tên thư mục
 *     description: Đổi tên một thư mục hiện có trong khu vực dữ liệu của người dùng. Không được phép đổi tên thư mục gốc.
 *     tags: [Data]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - folderPath
 *               - newFolderName
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               folderPath:
 *                 type: string
 *                 description: Đường dẫn của thư mục cần đổi tên (tính từ thư mục gốc của user)
 *                 example: hinhanh/2026
 *               newFolderName:
 *                 type: string
 *                 description: Tên mới cho thư mục (không bao gồm đường dẫn)
 *                 example: 2026_moi
 *     responses:
 *       200:
 *         description: Đổi tên thư mục thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Đổi tên thư mục thành công
 *                 data:
 *                   type: object
 *       400:
 *         description: Dữ liệu không hợp lệ, đường dẫn trỏ tới file, cố tình đổi tên thư mục gốc, hoặc tên mới đã tồn tại
 *       403:
 *         description: Đường dẫn không hợp lệ (bị lỗi Path Traversal)
 *       404:
 *         description: Thư mục không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 */
router.post('/rename-folder', authenticateToken, renameFolder);

/**
 * @swagger
 * /api/data/search:
 *   post:
 *     summary: Tìm kiếm file và thư mục
 *     description: Tìm kiếm file và thư mục theo tên (từ khoá) trong toàn bộ khu vực dữ liệu của người dùng. Trả về đường dẫn tương đối để dễ dàng truy cập.
 *     tags: [Data]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - keyword
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               keyword:
 *                 type: string
 *                 description: Từ khoá cần tìm kiếm trong tên file/thư mục (không phân biệt hoa thường)
 *                 example: avatar
 *     responses:
 *       200:
 *         description: Tìm kiếm thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Tìm kiếm thành công
 *                 data:
 *                   type: object
 *       400:
 *         description: Thiếu dữ liệu (username, keyword)
 *       404:
 *         description: Thư mục người dùng không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 */
router.post('/search', authenticateToken, searchFiles);

/**
 * @swagger
 * /api/data/convert:
 *   post:
 *     summary: Chuyển đổi định dạng file
 *     description: |
 *       Chuyển đổi một file sang định dạng khác ngay trên server. Hỗ trợ 3 loại engine:
 *       - **Ảnh** (Sharp): jpg, jpeg, png, webp, bmp, tiff, gif, avif
 *       - **Audio/Video** (FFmpeg): mp4, mkv, avi, mov, wmv, flv, webm, m4v, mp3, wav, m4a, ogg, aac, flac, wma
 *       - **Tài liệu** (LibreOffice): docx, doc, xlsx, xls, pptx, ppt, odt → pdf
 *
 *       **Lưu ý:** Quá trình chuyển đổi video có thể mất nhiều thời gian tùy vào kích thước file và hiệu năng server.
 *     tags: [Data]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - filePath
 *               - targetFormat
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               filePath:
 *                 type: string
 *                 description: Đường dẫn của file cần chuyển đổi (tính từ thư mục gốc của user)
 *                 example: Documents/BaoCao.docx
 *               targetFormat:
 *                 type: string
 *                 description: Định dạng đích (không cần dấu chấm, ví dụ "pdf", "mp3", "webp")
 *                 example: pdf
 *     responses:
 *       200:
 *         description: Chuyển đổi thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Chuyển đổi thành công
 *                 data:
 *                   type: object
 *                   properties:
 *                     originalPath:
 *                       type: string
 *                       description: Đường dẫn tương đối của file gốc
 *                       example: Documents/BaoCao.docx
 *                     newPath:
 *                       type: string
 *                       description: Đường dẫn tương đối của file đã được chuyển đổi
 *                       example: Documents/BaoCao.pdf
 *       400:
 *         description: Thiếu dữ liệu, định dạng không hợp lệ, hoặc định dạng không được hỗ trợ
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 error:
 *                   type: string
 *                   example: Định dạng không được hỗ trợ
 *       403:
 *         description: Đường dẫn không hợp lệ (Path Traversal)
 *       404:
 *         description: File nguồn không tồn tại
 *       500:
 *         description: Lỗi trong quá trình chuyển đổi
 */
router.post('/convert', authenticateToken, convertFile);

/**
 * @swagger
 * /api/data/compress:
 *   post:
 *     summary: Nén file/thư mục thành file .zip
 *     description: |
 *       Đóng gói nhiều file và/hoặc thư mục được chỉ định thành một file `.zip` duy nhất, lưu tại thư mục đích trên server.
 *       - `targetPath`: thư mục sẽ chứa file zip được tạo ra.
 *       - `items`: mảng đường dẫn tương đối (tính từ thư mục gốc của user) của các file/thư mục cần nén.
 *       - Các item không tồn tại hoặc có đường dẫn không hợp lệ sẽ bị bỏ qua.
 *     tags: [Data]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - targetPath
 *               - zipName
 *               - items
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               targetPath:
 *                 type: string
 *                 description: Đường dẫn thư mục đích sẽ lưu file zip (tính từ thư mục gốc của user). Truyền chuỗi rỗng `""` để lưu ở thư mục gốc.
 *                 example: /
 *               zipName:
 *                 type: string
 *                 description: Tên file zip sẽ được tạo (phải kết thúc bằng .zip)
 *                 example: TaiLieu_Nen.zip
 *               items:
 *                 type: array
 *                 description: Danh sách đường dẫn tương đối của các file hoặc thư mục cần nén
 *                 items:
 *                   type: string
 *                 example: ["/BaoCao.docx", "/HinhAnh/Anh1.png", "/ThuMucChuaData"]
 *     responses:
 *       200:
 *         description: Nén file thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Nén file thành công
 *                 data:
 *                   type: object
 *                   properties:
 *                     zipFilePath:
 *                       type: string
 *                       description: Đường dẫn tương đối của file zip vừa được tạo
 *                       example: /TaiLieu_Nen.zip
 *       400:
 *         description: Thiếu dữ liệu, tên file zip không hợp lệ, hoặc không có item hợp lệ nào
 *       403:
 *         description: Đường dẫn không hợp lệ (Path Traversal)
 *       404:
 *         description: Thư mục đích không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 */
router.post('/compress', authenticateToken, compressFiles);

/**
 * @swagger
 * /api/data/extract:
 *   post:
 *     summary: Giải nén file .zip
 *     description: |
 *       Giải nén một file `.zip` có sẵn trên server vào thư mục đích được chỉ định.
 *       - Thư mục đích sẽ được tạo tự động nếu chưa tồn tại.
 *       - Nếu có file trùng tên, file cũ sẽ bị ghi đè.
 *     tags: [Data]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - filePath
 *               - extractToPath
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               filePath:
 *                 type: string
 *                 description: Đường dẫn của file .zip cần giải nén (tính từ thư mục gốc của user)
 *                 example: /TaiLieu_Nen.zip
 *               extractToPath:
 *                 type: string
 *                 description: Đường dẫn thư mục đích để giải nén vào (tính từ thư mục gốc của user). Sẽ được tạo tự động nếu chưa tồn tại.
 *                 example: /TaiLieu_Nen_Giai_Nen
 *     responses:
 *       200:
 *         description: Giải nén thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Giải nén thành công
 *                 data:
 *                   type: object
 *                   properties:
 *                     extractPath:
 *                       type: string
 *                       description: Đường dẫn tương đối của thư mục chứa nội dung đã giải nén
 *                       example: /TaiLieu_Nen_Giai_Nen
 *       400:
 *         description: Thiếu dữ liệu hoặc file không phải định dạng .zip
 *       403:
 *         description: Đường dẫn không hợp lệ (Path Traversal)
 *       404:
 *         description: Không tìm thấy file zip
 *       500:
 *         description: Lỗi hệ thống server
 */
router.post('/extract', authenticateToken, extractFile);

/**
 * @swagger
 * /api/data/share:
 *   post:
 *     summary: Chia sẻ file cho người dùng khác
 *     description: |
 *       Tạo bản ghi chia sẻ trong bảng `share_file`. File phải tồn tại trong thư mục của owner.
 *       Nếu bản ghi đã tồn tại, request sẽ không tạo trùng lặp.
 *     tags: [Share]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - ownerUsername
 *               - targetUsername
 *               - filePath
 *             properties:
 *               ownerUsername:
 *                 type: string
 *                 description: Username của người sở hữu file
 *                 example: admin123
 *               targetUsername:
 *                 type: string
 *                 description: Username của người được chia sẻ
 *                 example: user456
 *               filePath:
 *                 type: string
 *                 description: Đường dẫn tương đối của file cần chia sẻ (tính từ thư mục gốc của owner)
 *                 example: BaoCao.docx
 *     responses:
 *       201:
 *         description: Chia sẻ file thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Chia sẻ file thành công
 *                 data:
 *                   type: object
 *       400:
 *         description: Thiếu dữ liệu hoặc tự chia sẻ cho chính mình
 *       403:
 *         description: Đường dẫn file không hợp lệ (Path Traversal)
 *       404:
 *         description: Người dùng hoặc file không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 *   delete:
 *     summary: Huỷ chia sẻ file
 *     description: Xoá bản ghi chia sẻ khỏi bảng `share_file`.
 *     tags: [Share]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - ownerUsername
 *               - targetUsername
 *               - filePath
 *             properties:
 *               ownerUsername:
 *                 type: string
 *                 example: admin123
 *               targetUsername:
 *                 type: string
 *                 example: user456
 *               filePath:
 *                 type: string
 *                 example: BaoCao.docx
 *     responses:
 *       200:
 *         description: Huỷ chia sẻ thành công
 *       400:
 *         description: Thiếu dữ liệu
 *       404:
 *         description: Người dùng hoặc bản ghi chia sẻ không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 *   put:
 *     summary: Cập nhật đường dẫn file chia sẻ
 *     description: Cập nhật `file_path` trong bảng `share_file` khi file được đổi tên hoặc di chuyển. File mới phải tồn tại trong thư mục của owner.
 *     tags: [Share]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - ownerUsername
 *               - targetUsername
 *               - oldFilePath
 *               - newFilePath
 *             properties:
 *               ownerUsername:
 *                 type: string
 *                 example: admin123
 *               targetUsername:
 *                 type: string
 *                 example: user456
 *               oldFilePath:
 *                 type: string
 *                 description: Đường dẫn cũ của file
 *                 example: BaoCao.docx
 *               newFilePath:
 *                 type: string
 *                 description: Đường dẫn mới của file
 *                 example: TaiLieu/BaoCao.docx
 *     responses:
 *       200:
 *         description: Cập nhật chia sẻ thành công
 *       400:
 *         description: Thiếu dữ liệu
 *       403:
 *         description: Đường dẫn file mới không hợp lệ (Path Traversal)
 *       404:
 *         description: Người dùng, file mới, hoặc bản ghi chia sẻ không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 */
router.post('/share', authenticateToken, shareFile);
router.delete('/share', authenticateToken, unshareFile);
router.put('/share', authenticateToken, updateShare);

/**
 * @swagger
 * /api/data/share/by-owner:
 *   get:
 *     summary: Lấy danh sách file owner đang chia sẻ
 *     description: |
 *       Trả về danh sách những người đang được chia sẻ file của owner.
 *       - Nếu truyền thêm `filePath` → chỉ lấy những ai được chia sẻ file đó cụ thể.
 *       - Nếu không truyền `filePath` → trả về toàn bộ (tất cả file và người nhận tương ứng).
 *     tags: [Share]
 *     security:
 *       - cookieAuth: []
 *     parameters:
 *       - in: query
 *         name: ownerUsername
 *         schema:
 *           type: string
 *         required: true
 *         description: Username của người sở hữu file
 *         example: admin123
 *       - in: query
 *         name: filePath
 *         schema:
 *           type: string
 *         required: false
 *         description: Đường dẫn file cần lọc (tính từ thư mục gốc của owner). Bỏ qua nếu muốn lấy tất cả.
 *         example: BaoCao.docx
 *     responses:
 *       200:
 *         description: Lấy danh sách thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Lấy danh sách file đang chia sẻ thành công
 *                 data:
 *                   type: array
 *                   items:
 *                     type: object
 *                     properties:
 *                       file_path:
 *                         type: string
 *                         example: BaoCao.docx
 *                       targetUsername:
 *                         type: string
 *                         example: user456
 *       400:
 *         description: Thiếu tham số ownerUsername
 *       404:
 *         description: Không tìm thấy owner
 *       500:
 *         description: Lỗi hệ thống server
 */
router.get('/share/by-owner', authenticateToken, getSharedByOwner);

/**
 * @swagger
 * /api/data/share/to-me:
 *   get:
 *     summary: Lấy danh sách file được chia sẻ đến mình
 *     description: |
 *       Trả về toàn bộ file mà người khác đã chia sẻ cho `targetUsername`.
 *       Mỗi bản ghi gồm đường dẫn file và thông tin người đã chia sẻ (id, username, full_name).
 *     tags: [Share]
 *     security:
 *       - cookieAuth: []
 *     parameters:
 *       - in: query
 *         name: targetUsername
 *         schema:
 *           type: string
 *         required: true
 *         description: Username của người nhận chia sẻ
 *         example: user456
 *     responses:
 *       200:
 *         description: Lấy danh sách thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Lấy danh sách file được chia sẻ thành công
 *                 data:
 *                   type: array
 *                   items:
 *                     type: object
 *                     properties:
 *                       file_path:
 *                         type: string
 *                         example: BaoCao.docx
 *                       ownerUsername:
 *                         type: string
 *                         example: admin123
 *       400:
 *         description: Thiếu tham số targetUsername
 *       404:
 *         description: Không tìm thấy target
 *       500:
 *         description: Lỗi hệ thống server
 */
router.get('/share/to-me', authenticateToken, getSharedToMe);

export default router;
