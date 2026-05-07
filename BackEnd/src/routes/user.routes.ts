import { Router } from 'express';
import { createUser, login } from '../controllers/user.controller';

const router = Router();

/**
 * @swagger
 * /api/users:
 *   post:
 *     summary: Tạo mới user
 *     tags: [Users]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - password
 *             properties:
 *               username:
 *                 type: string
 *                 example: admin123
 *               password:
 *                 type: string
 *                 example: 123456
 *     responses:
 *       201:
 *         description: Tạo user thành công
 *       400:
 *         description: Dữ liệu không hợp lệ
 *       500:
 *         description: Lỗi server
 */
router.post('/', createUser);

/**
 * @swagger
 * /api/users/login:
 *   post:
 *     summary: Đăng nhập user
 *     tags: [Users]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - password
 *             properties:
 *               username:
 *                 type: string
 *                 example: admin123
 *               password:
 *                 type: string
 *                 example: 123456
 *     responses:
 *       200:
 *         description: Đăng nhập thành công
 *       400:
 *         description: Dữ liệu không hợp lệ
 *       401:
 *         description: Tài khoản hoặc mật khẩu không chính xác
 *       500:
 *         description: Lỗi server
 */
router.post('/login', login);

export default router;