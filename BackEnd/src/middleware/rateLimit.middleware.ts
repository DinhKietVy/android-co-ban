
import rateLimit from "express-rate-limit";
import type { Request, Response } from "express";

export const rateLimitMiddleware = rateLimit({
    // Khoảng thời gian giới hạn: 5 phút
    windowMs: 5 * 60 * 1000,

    // Mỗi IP tối đa 5 request
    limit: 5,

    // Sử dụng các header Rate Limit tiêu chuẩn
    standardHeaders: "draft-8",

    // Tắt header cũ X-RateLimit-*
    legacyHeaders: false,

    // Thông báo mặc định
    message: {
        success: false,
        message: "Too many requests. Please try again later."
    },

    // Xử lý khi client vượt quá giới hạn
    handler: (
        req: Request,
        res: Response
    ) => {
        res.status(429).json({
            success: false,
            statusCode: 429,
            message: "Too many requests. Please try again later.",
            retryAfter: res.getHeader("Retry-After")
        });
    }
});
