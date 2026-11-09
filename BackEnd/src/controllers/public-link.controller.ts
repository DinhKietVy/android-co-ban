import { Request, Response } from 'express';
import { connectDB } from '../config/database';
import sql from 'mssql';
import { randomUUID } from 'crypto';
import path from 'path';
import fs from 'fs';
import { AuthRequest } from '../types';

const DATA_DIR = path.join(__dirname, '../../data');

// ─── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Tính thời điểm hết hạn từ chuỗi như "1h", "7d", "30d".
 * Trả về null nếu không hết hạn (link vĩnh viễn).
 */
const parseExpiresAt = (expiresIn: string | undefined): Date | null => {
    if (!expiresIn) return null;
    const match = String(expiresIn).match(/^(\d+)([hHdD])$/);
    if (!match) return null;
    const value = parseInt(match[1], 10);
    const unit = match[2].toLowerCase();
    const ms = unit === 'h' ? value * 60 * 60 * 1000 : value * 24 * 60 * 60 * 1000;
    return new Date(Date.now() + ms);
};

/**
 * Kiểm tra link có còn hạn không.
 */
const isLinkExpired = (expiresAt: Date | null): boolean => {
    if (!expiresAt) return false;
    return new Date() > new Date(expiresAt);
};

// ─── Controllers ─────────────────────────────────────────────────────────────

/**
 * POST /api/public-link/create
 * Tạo link chia sẻ công khai cho một file hoặc folder.
 * Yêu cầu xác thực JWT.
 */
export const createPublicLink = async (req: AuthRequest, res: Response): Promise<void> => {
    try {
        const userId = req.user?.id;
        if (!userId) {
            res.status(401).json({ error: 'Khong the xac thuc nguoi dung' });
            return;
        }

        const { filePath, expiresIn } = req.body;

        if (!filePath) {
            res.status(400).json({ error: 'Thieu filePath' });
            return;
        }

        // Kiểm tra file/folder có tồn tại không
        const absolutePath = path.join(DATA_DIR, filePath);
        if (!fs.existsSync(absolutePath)) {
            res.status(404).json({ error: 'File hoac thu muc khong ton tai' });
            return;
        }

        // Kiểm tra quyền sở hữu: đường dẫn phải bắt đầu bằng username của user
        const pool = await connectDB();
        const userResult = await pool.request()
            .input('id', sql.Int, userId)
            .query('SELECT username FROM users WHERE id = @id');

        if (userResult.recordset.length === 0) {
            res.status(404).json({ error: 'Khong tim thay nguoi dung' });
            return;
        }

        const ownerUsername = userResult.recordset[0].username;
        const normalizedFilePath = filePath.replace(/\\/g, '/');
        if (!normalizedFilePath.startsWith(ownerUsername + '/') && normalizedFilePath !== ownerUsername) {
            res.status(403).json({ error: 'Khong co quyen chia se file nay' });
            return;
        }

        // Kiểm tra đã có link chưa (mỗi file chỉ cần 1 link, cập nhật nếu đã có)
        const existingResult = await pool.request()
            .input('owner_id', sql.Int, userId)
            .input('file_path', sql.VarChar(255), filePath)
            .query(`
                SELECT token FROM public_links
                WHERE owner_id = @owner_id AND file_path = @file_path
            `);

        const expiresAt = parseExpiresAt(expiresIn);
        const stats = fs.statSync(absolutePath);
        const isDirectory = stats.isDirectory();

        if (existingResult.recordset.length > 0) {
            // Cập nhật expires_at cho link cũ
            const existingToken = existingResult.recordset[0].token;
            await pool.request()
                .input('token', sql.VarChar(36), existingToken)
                .input('expires_at', sql.DateTime, expiresAt)
                .query(`
                    UPDATE public_links
                    SET expires_at = @expires_at, created_at = GETDATE()
                    WHERE token = @token
                `);

            res.json({
                token: existingToken,
                expiresAt: expiresAt ?? null,
                isDirectory,
                message: 'Da cap nhat link chia se',
            });
            return;
        }

        const token = randomUUID();

        await pool.request()
            .input('token', sql.VarChar(36), token)
            .input('owner_id', sql.Int, userId)
            .input('file_path', sql.VarChar(255), filePath)
            .input('expires_at', sql.DateTime, expiresAt)
            .query(`
                INSERT INTO public_links (token, owner_id, file_path, expires_at)
                VALUES (@token, @owner_id, @file_path, @expires_at)
            `);

        res.json({
            token,
            expiresAt: expiresAt ?? null,
            isDirectory,
            message: 'Da tao link chia se thanh cong',
        });
    } catch (error: any) {
        console.error('Loi khi tao public link:', error);
        res.status(500).json({ error: error.message });
    }
};

/**
 * GET /api/public-link/:token/info
 * Lấy thông tin file/folder qua link chia sẻ. Không cần xác thực.
 */
export const getPublicLinkInfo = async (req: Request, res: Response): Promise<void> => {
    try {
        const { token } = req.params;

        const pool = await connectDB();
        const result = await pool.request()
            .input('token', sql.VarChar(36), token)
            .query(`
                SELECT p.file_path, p.expires_at, u.username AS owner_username
                FROM public_links p
                JOIN users u ON p.owner_id = u.id
                WHERE p.token = @token
            `);

        if (result.recordset.length === 0) {
            res.status(404).json({ error: 'Link khong ton tai' });
            return;
        }

        const record = result.recordset[0];

        if (isLinkExpired(record.expires_at)) {
            res.status(410).json({ error: 'Link da het han' });
            return;
        }

        const absolutePath = path.join(DATA_DIR, record.file_path);

        if (!fs.existsSync(absolutePath)) {
            res.status(404).json({ error: 'Khong tim thay file tren server' });
            return;
        }

        const stats = fs.statSync(absolutePath);
        const isDirectory = stats.isDirectory();
        const fileName = path.basename(record.file_path);

        let items: { name: string; size: number; isDirectory: boolean }[] | undefined;
        if (isDirectory) {
            items = fs.readdirSync(absolutePath).map((name) => {
                const itemPath = path.join(absolutePath, name);
                const itemStats = fs.statSync(itemPath);
                return { name, size: itemStats.size, isDirectory: itemStats.isDirectory() };
            });
        }

        res.json({
            fileName,
            ownerUsername: record.owner_username,
            size: stats.size,
            filePath: record.file_path,
            isDirectory,
            expiresAt: record.expires_at ?? null,
            ...(isDirectory && { items }),
        });
    } catch (error: any) {
        console.error('Loi khi lay thong tin public link:', error);
        res.status(500).json({ error: error.message });
    }
};

/**
 * GET /api/public-link/:token/download
 * Tải file qua link chia sẻ. Không cần xác thực.
 * Chỉ hỗ trợ file đơn (không hỗ trợ tải folder trực tiếp).
 */
export const downloadPublicLink = async (req: Request, res: Response): Promise<void> => {
    try {
        const { token } = req.params;

        const pool = await connectDB();
        const result = await pool.request()
            .input('token', sql.VarChar(36), token)
            .query(`
                SELECT file_path, expires_at
                FROM public_links
                WHERE token = @token
            `);

        if (result.recordset.length === 0) {
            res.status(404).json({ error: 'Link khong ton tai' });
            return;
        }

        const { file_path: filePath, expires_at: expiresAt } = result.recordset[0];

        if (isLinkExpired(expiresAt)) {
            res.status(410).json({ error: 'Link da het han' });
            return;
        }

        const absolutePath = path.join(DATA_DIR, filePath);

        if (!fs.existsSync(absolutePath)) {
            res.status(404).json({ error: 'File khong ton tai tren server' });
            return;
        }

        const stats = fs.statSync(absolutePath);
        if (stats.isDirectory()) {
            res.status(400).json({ error: 'Khong the tai truc tiep thu muc. Vui long tai tung file.' });
            return;
        }

        res.download(absolutePath);
    } catch (error: any) {
        console.error('Loi khi tai file qua public link:', error);
        res.status(500).json({ error: error.message });
    }
};

/**
 * DELETE /api/public-link
 * Thu hồi link chia sẻ. Yêu cầu xác thực JWT.
 */
export const deletePublicLink = async (req: AuthRequest, res: Response): Promise<void> => {
    try {
        const userId = req.user?.id;
        if (!userId) {
            res.status(401).json({ error: 'Khong the xac thuc nguoi dung' });
            return;
        }

        const { filePath, token } = req.body;

        const pool = await connectDB();
        let deleteResult;

        if (token) {
            // Thu hồi theo token cụ thể
            deleteResult = await pool.request()
                .input('token', sql.VarChar(36), String(token))
                .input('owner_id', sql.Int, userId)
                .query(`
                    DELETE FROM public_links
                    WHERE token = @token AND owner_id = @owner_id
                `);
        } else if (filePath) {
            // Thu hồi theo đường dẫn file
            deleteResult = await pool.request()
                .input('owner_id', sql.Int, userId)
                .input('file_path', sql.VarChar(255), String(filePath))
                .query(`
                    DELETE FROM public_links
                    WHERE owner_id = @owner_id AND file_path = @file_path
                `);
        } else {
            res.status(400).json({ error: 'Can cung cap token hoac filePath' });
            return;
        }

        if (deleteResult.rowsAffected[0] > 0) {
            res.json({ message: 'Da thu hoi quyen truy cap cong khai' });
        } else {
            res.status(404).json({ error: 'Khong co lien ket nao de thu hoi hoac ban khong co quyen' });
        }
    } catch (error: any) {
        console.error('Loi khi xoa public link:', error);
        res.status(500).json({ error: error.message });
    }
};

/**
 * GET /api/public-link/my-links
 * Lấy danh sách tất cả link chia sẻ của người dùng hiện tại.
 */
export const getMyPublicLinks = async (req: AuthRequest, res: Response): Promise<void> => {
    try {
        const userId = req.user?.id;
        if (!userId) {
            res.status(401).json({ error: 'Khong the xac thuc nguoi dung' });
            return;
        }

        const pool = await connectDB();
        const result = await pool.request()
            .input('owner_id', sql.Int, userId)
            .query(`
                SELECT token, file_path, created_at, expires_at
                FROM public_links
                WHERE owner_id = @owner_id
                ORDER BY created_at DESC
            `);

        const links = result.recordset.map((row) => ({
            token: row.token,
            filePath: row.file_path,
            fileName: path.basename(row.file_path),
            createdAt: row.created_at,
            expiresAt: row.expires_at ?? null,
            isExpired: isLinkExpired(row.expires_at),
        }));

        res.json({ data: links });
    } catch (error: any) {
        console.error('Loi khi lay danh sach public link:', error);
        res.status(500).json({ error: error.message });
    }
};
