import { Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import { AuthRequest } from '../types';
import { generateAccessToken } from '../utils/token';
export const authenticateToken = (req: AuthRequest, res: Response, next: NextFunction): void => {
    const token = req.cookies?.accessToken;
    const refreshToken = req.cookies?.refreshToken;

    // 👉 1. Không có access token
    if (!token) {
        if (!refreshToken) {
            res.status(401).json({ message: 'Không có token' });
            return;
        }

        // 👉 verify refresh token
        jwt.verify(refreshToken, process.env.JWT_SECRET!, (err2: any, decoded2: any) => {
            if (err2 || !decoded2) {
                return res.status(403).json({ message: 'Refresh token không hợp lệ' });
            }

            // 1. Tự động tạo access token mới (KHÔNG gọi controller)
            const user = decoded2 as { id: string | number; account: string };
            const newAccessToken = generateAccessToken({ id: user.id, account: user.account || '' });
            // 2. Set cookie mới
            res.cookie('accessToken', newAccessToken, {
                httpOnly: true,
                secure: process.env.NODE_ENV === 'production',
                sameSite: 'strict',
                maxAge: 15 * 60 * 1000,
            });
            // 3. Quan trọng nhất: Gán user và đi tiếp để lấy dữ liệu luôn
            req.user = { id: user.id, account: user.account || '' };
            next();
        });

        return;
    }

    // 👉 2. Có access token → verify
    jwt.verify(token, process.env.JWT_SECRET!, (err: any, decoded: any) => {
        if (err || !decoded) {
            return res.status(403).json({ message: 'Token không hợp lệ' });
        }

        req.user = decoded as { id: string | number; account: string };

        return next();
    });
};