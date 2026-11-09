import { Router } from 'express';
import { createUser, googleAuth, login, forgotPassword, verifyResetCode, resetPassword, autoLogin, updateProfile, changePassword, deleteAccount, verifyDeviceOTP, getTrustedDevices, removeTrustedDevice, removeAllTrustedDevices } from '../controllers/user.controller';
import { authenticateToken } from '../middleware/auth';

const router = Router();

/**
 * @swagger
 * /api/users:
 *   post:
 *     summary: Tạo người dùng mới
 *     description: Đăng ký một tài khoản người dùng mới vào hệ thống.
 *     tags: [Users]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - fullName
 *               - email
 *               - password
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập
 *               fullName:
 *                 type: string
 *                 description: Họ và tên
 *               email:
 *                 type: string
 *                 description: Địa chỉ email
 *               password:
 *                 type: string
 *                 description: Mật khẩu
 *     responses:
 *       201:
 *         description: Tạo thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Tao thanh cong
 *                 data:
 *                   type: object
 *                   properties:
 *                     id:
 *                       type: integer
 *                     username:
 *                       type: string
 *                     fullName:
 *                       type: string
 *                     email:
 *                       type: string
 *       400:
 *         description: Thiếu thông tin bắt buộc hoặc tài khoản/email đã tồn tại
 *       500:
 *         description: Lỗi máy chủ
 */
router.post('/', createUser);

/**
 * @swagger
 * /api/users/google-auth:
 *   post:
 *     summary: Đăng nhập hoặc đăng ký bằng Google
 *     description: Đăng nhập bằng tài khoản Google. Nếu tài khoản chưa tồn tại sẽ tự động đăng ký mới.
 *     tags: [Users]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - googleUid
 *             properties:
 *               username:
 *                 type: string
 *                 description: Tên đăng nhập (lấy từ Google)
 *               email:
 *                 type: string
 *                 description: Địa chỉ email
 *               displayName:
 *                 type: string
 *                 description: Tên hiển thị
 *               googleUid:
 *                 type: string
 *                 description: UID từ Firebase/Google Auth
 *     responses:
 *       200:
 *         description: Đăng nhập Google thành công
 *       201:
 *         description: Tạo tài khoản Google thành công
 *       400:
 *         description: Thiếu thông tin bắt buộc hoặc lỗi dữ liệu
 *       500:
 *         description: Lỗi máy chủ
 */
router.post('/google-auth', googleAuth);

/**
 * @swagger
 * /api/users/login:
 *   post:
 *     summary: Đăng nhập
 *     description: Đăng nhập vào hệ thống bằng username hoặc email và password.
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
 *                 description: Tên đăng nhập hoặc email
 *               password:
 *                 type: string
 *                 description: Mật khẩu
 *     responses:
 *       200:
 *         description: Đăng nhập thành công
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 message:
 *                   type: string
 *                   example: Dang nhap thanh cong
 *                 data:
 *                   type: object
 *                   properties:
 *                     id:
 *                       type: integer
 *                     username:
 *                       type: string
 *                     fullName:
 *                       type: string
 *                     email:
 *                       type: string
 *       400:
 *         description: Thiếu thông tin bắt buộc
 *       401:
 *         description: Tài khoản hoặc mật khẩu không chính xác
 *       500:
 *         description: Lỗi máy chủ
 */
router.post('/login', login);

/**
 * @swagger
 * /api/users/forgot-password:
 *   post:
 *     summary: Quên mật khẩu
 *     description: Gửi mã đặt lại mật khẩu đến email của người dùng.
 *     tags: [Users]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - email
 *             properties:
 *               email:
 *                 type: string
 *                 description: Địa chỉ email của người dùng
 *     responses:
 *       200:
 *         description: Đã gửi mã thành công
 *       400:
 *         description: Lỗi dữ liệu gửi lên
 *       404:
 *         description: Không tìm thấy email
 *       500:
 *         description: Lỗi máy chủ
 */
router.post('/forgot-password', forgotPassword);
router.post('/verify-reset-code', verifyResetCode);

/**
 * @swagger
 * /api/users/reset-password:
 *   post:
 *     summary: Đặt lại mật khẩu
 *     description: Đặt lại mật khẩu sử dụng mã xác nhận nhận được từ email.
 *     tags: [Users]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - email
 *               - code
 *               - newPassword
 *             properties:
 *               email:
 *                 type: string
 *                 description: Địa chỉ email
 *               code:
 *                 type: string
 *                 description: Mã xác nhận (6 chữ số)
 *               newPassword:
 *                 type: string
 *                 description: Mật khẩu mới
 *     responses:
 *       200:
 *         description: Đặt lại mật khẩu thành công
 *       400:
 *         description: Lỗi dữ liệu hoặc mã xác nhận không hợp lệ / đã hết hạn
 *       404:
 *         description: Không tìm thấy email
 *       500:
 *         description: Lỗi máy chủ
 */
router.post('/reset-password', resetPassword);

/**
 * @swagger
 * /api/users/auto-login:
 *   get:
 *     summary: Tự động đăng nhập
 *     description: Tự động đăng nhập người dùng dựa trên JWT (accessToken hoặc refreshToken trong cookies).
 *     tags: [Users]
 *     responses:
 *       200:
 *         description: Tự động đăng nhập thành công
 *       401:
 *         description: Không có token hoặc token không hợp lệ
 *       404:
 *         description: Không tìm thấy người dùng
 *       500:
 *         description: Lỗi máy chủ
 */
router.get('/auto-login', authenticateToken, autoLogin);

router.put('/me', authenticateToken, updateProfile);
router.post('/change-password', authenticateToken, changePassword);
router.delete('/me', authenticateToken, deleteAccount);

/**
 * @swagger
 * /api/users/verify-device-otp:
 *   post:
 *     summary: Xác minh OTP thiết bị mới (2FA)
 *     description: Xác minh mã OTP được gửi đến email khi đăng nhập từ thiết bị lạ. Nếu thành công sẽ issue cookie và tùy chọn thêm thiết bị vào danh sách tin tưởng.
 *     tags: [Users]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - tempToken
 *               - otpCode
 *             properties:
 *               tempToken:
 *                 type: string
 *                 description: Token tạm thời nhận được từ response login
 *               otpCode:
 *                 type: string
 *                 description: Mã OTP 6 số nhận qua email
 *               trustDevice:
 *                 type: boolean
 *                 description: Có tin tưởng thiết bị này không (mặc định true)
 *     responses:
 *       200:
 *         description: Xác minh thành công, cookie được set
 *       400:
 *         description: OTP không đúng hoặc hết hạn
 *       500:
 *         description: Lỗi máy chủ
 */
router.post('/verify-device-otp', verifyDeviceOTP);

/**
 * @swagger
 * /api/users/trusted-devices:
 *   get:
 *     summary: Lấy danh sách thiết bị tin tưởng
 *     tags: [Users]
 *     responses:
 *       200:
 *         description: Danh sách thiết bị
 *   delete:
 *     summary: Xóa tất cả thiết bị tin tưởng
 *     tags: [Users]
 *     responses:
 *       200:
 *         description: Đã xóa tất cả
 */
router.get('/trusted-devices', authenticateToken, getTrustedDevices);
router.delete('/trusted-devices', authenticateToken, removeAllTrustedDevices);

/**
 * @swagger
 * /api/users/trusted-devices/{deviceId}:
 *   delete:
 *     summary: Xóa một thiết bị tin tưởng
 *     tags: [Users]
 *     parameters:
 *       - in: path
 *         name: deviceId
 *         required: true
 *         schema:
 *           type: integer
 *     responses:
 *       200:
 *         description: Đã xóa
 *       404:
 *         description: Không tìm thấy thiết bị
 */
router.delete('/trusted-devices/:deviceId', authenticateToken, removeTrustedDevice);

export default router;
