import { Router } from 'express';
import { listBinFiles, restoreFile, deleteBinFile } from '../controllers/bin.controller';
import { authenticateToken } from '../middleware/auth';


const router = Router();

/**
 * @swagger
 * /api/bin/list:
 *   post:
 *     summary: Lấy danh sách file trong thùng rác
 *     description: Lấy toàn bộ danh sách các file hiện có trong thùng rác (bin) của người dùng.
 *     tags: [Bin]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
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
 *                     files:
 *                       type: array
 *                       items:
 *                         type: object
 *                         properties:
 *                           name:
 *                             type: string
 *                           size:
 *                             type: integer
 *                           deletedAt:
 *                             type: string
 *                             format: date-time
 *       400:
 *         description: Thiếu dữ liệu (username)
 *       500:
 *         description: Lỗi hệ thống server
 */
router.post('/list',authenticateToken, listBinFiles);

/**
 * @swagger
 * /api/bin/restore:
 *   post:
 *     summary: Khôi phục file từ thùng rác
 *     description: Khôi phục một file bị xoá từ thùng rác. File sẽ được đưa vào thư mục `recover` nằm trong thư mục gốc của người dùng.
 *     tags: [Bin]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - fileName
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               fileName:
 *                 type: string
 *                 description: Tên của file hiện đang nằm trong thùng rác (bao gồm cả chuỗi thời gian nếu có)
 *                 example: 1684323456789-avatar.png
 *     responses:
 *       200:
 *         description: Khôi phục file thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Khôi phục file thành công
 *                 data:
 *                   type: object
 *                   properties:
 *                     username:
 *                       type: string
 *                     recoveredTo:
 *                       type: string
 *                       description: Đường dẫn file sau khi khôi phục
 *                       example: recover/avatar.png
 *       400:
 *         description: Dữ liệu không hợp lệ hoặc đường dẫn trỏ tới thư mục
 *       403:
 *         description: Đường dẫn không hợp lệ (bị lỗi Path Traversal)
 *       404:
 *         description: File trong thùng rác không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 */
router.post('/restore',authenticateToken, restoreFile);

/**
 * @swagger
 * /api/bin/file:
 *   delete:
 *     summary: Xoá vĩnh viễn file
 *     description: Xoá vĩnh viễn một file đang nằm trong thùng rác. Hành động này không thể hoàn tác.
 *     tags: [Bin]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - fileName
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập của người dùng
 *                 example: admin123
 *               fileName:
 *                 type: string
 *                 description: Tên của file hiện đang nằm trong thùng rác
 *                 example: 1684323456789-avatar.png
 *     responses:
 *       200:
 *         description: Xoá vĩnh viễn file thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Xoá vĩnh viễn file thành công
 *                 data:
 *                   type: object
 *       400:
 *         description: Dữ liệu không hợp lệ hoặc đường dẫn trỏ tới thư mục
 *       403:
 *         description: Đường dẫn không hợp lệ (bị lỗi Path Traversal)
 *       404:
 *         description: File trong thùng rác không tồn tại
 *       500:
 *         description: Lỗi hệ thống server
 */
router.delete('/file',authenticateToken, deleteBinFile);

export default router;