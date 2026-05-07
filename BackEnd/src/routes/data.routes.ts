import { Router } from 'express';
import { uploadData, createFolder, moveFile, moveFolder, deleteFile, deleteFolder, listDirectory } from '../controllers/data.controller';

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
router.post('/upload', uploadData);

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
router.post('/folder', createFolder);

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
router.post('/move-file', moveFile);

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
router.post('/move-folder', moveFolder);

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
router.delete('/file', deleteFile);

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
router.delete('/folder', deleteFolder);

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
 *                   properties:
 *                     username:
 *                       type: string
 *                       example: admin123
 *                     currentFolder:
 *                       type: string
 *                       example: hinhanh
 *                     folders:
 *                       type: array
 *                       items:
 *                         type: object
 *                         properties:
 *                           name:
 *                             type: string
 *                           createdAt:
 *                             type: string
 *                             format: date-time
 *                           modifiedAt:
 *                             type: string
 *                             format: date-time
 *                     files:
 *                       type: array
 *                       items:
 *                         type: object
 *                         properties:
 *                           name:
 *                             type: string
 *                           size:
 *                             type: integer
 *                             description: Kích thước file tính bằng byte
 *                           createdAt:
 *                             type: string
 *                             format: date-time
 *                           modifiedAt:
 *                             type: string
 *                             format: date-time
 *       400:
 *         description: Đường dẫn trỏ tới file thay vì thư mục, hoặc thiếu dữ liệu
 *       403:
 *         description: Đường dẫn không hợp lệ (bị lỗi Path Traversal)
 *       404:
 *         description: Thư mục không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 */
router.post('/list', listDirectory);

export default router;