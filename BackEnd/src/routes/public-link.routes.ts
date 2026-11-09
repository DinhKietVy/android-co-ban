import { Router } from 'express';
import {
    createPublicLink,
    getPublicLinkInfo,
    downloadPublicLink,
    deletePublicLink,
    getMyPublicLinks,
} from '../controllers/public-link.controller';
import { authenticateToken } from '../middleware/auth';

const router = Router();

/**
 * @swagger
 * /api/public-link/my-links:
 *   get:
 *     summary: Lấy danh sách link chia sẻ của tôi
 *     description: Trả về tất cả link chia sẻ công khai mà người dùng đã tạo, kèm trạng thái hết hạn.
 *     tags: [PublicLink]
 *     responses:
 *       200:
 *         description: Danh sách link
 *       401:
 *         description: Chưa xác thực
 */
router.get('/my-links', authenticateToken, getMyPublicLinks);

/**
 * @swagger
 * /api/public-link/create:
 *   post:
 *     summary: Tạo link chia sẻ công khai
 *     description: Tạo hoặc cập nhật link chia sẻ cho file/folder. Yêu cầu xác thực JWT.
 *     tags: [PublicLink]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - filePath
 *             properties:
 *               filePath:
 *                 type: string
 *                 description: Đường dẫn tương đối từ thư mục data (ví dụ "username/folder/file.txt")
 *               expiresIn:
 *                 type: string
 *                 description: Thời hạn link (ví dụ "1h", "7d", "30d"). Bỏ qua để tạo link vĩnh viễn.
 *     responses:
 *       200:
 *         description: Link đã tạo hoặc cập nhật thành công
 *       401:
 *         description: Chưa xác thực
 *       403:
 *         description: Không có quyền chia sẻ file này
 *       404:
 *         description: File không tồn tại
 */
router.post('/create', authenticateToken, createPublicLink);

/**
 * @swagger
 * /api/public-link:
 *   delete:
 *     summary: Thu hồi link chia sẻ
 *     description: Xóa link chia sẻ theo token hoặc filePath. Yêu cầu xác thực JWT và phải là chủ sở hữu.
 *     tags: [PublicLink]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               token:
 *                 type: string
 *                 description: UUID token của link
 *               filePath:
 *                 type: string
 *                 description: Đường dẫn file (thay thế cho token)
 *     responses:
 *       200:
 *         description: Đã thu hồi
 *       401:
 *         description: Chưa xác thực
 *       404:
 *         description: Không tìm thấy link
 */
router.delete('/', authenticateToken, deletePublicLink);

// Giữ route cũ POST /delete để tương thích ngược với mobile client cũ
router.post('/delete', authenticateToken, deletePublicLink);

/**
 * @swagger
 * /api/public-link/{token}/info:
 *   get:
 *     summary: Xem thông tin file qua link chia sẻ
 *     description: Lấy thông tin file/folder qua token. Không cần xác thực.
 *     tags: [PublicLink]
 *     parameters:
 *       - in: path
 *         name: token
 *         required: true
 *         schema:
 *           type: string
 *     responses:
 *       200:
 *         description: Thông tin file
 *       404:
 *         description: Link không tồn tại
 *       410:
 *         description: Link đã hết hạn
 */
router.get('/:token/info', getPublicLinkInfo);

/**
 * @swagger
 * /api/public-link/{token}/download:
 *   get:
 *     summary: Tải file qua link chia sẻ
 *     description: Download file qua token. Không cần xác thực. Chỉ hỗ trợ file đơn.
 *     tags: [PublicLink]
 *     parameters:
 *       - in: path
 *         name: token
 *         required: true
 *         schema:
 *           type: string
 *     responses:
 *       200:
 *         description: File stream
 *       404:
 *         description: Link hoặc file không tồn tại
 *       410:
 *         description: Link đã hết hạn
 */
router.get('/:token/download', downloadPublicLink);

export default router;
