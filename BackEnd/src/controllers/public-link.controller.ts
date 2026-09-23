import { Request, Response } from 'express';
import { sql } from '../config/db';
import { randomUUID } from 'crypto';
import path from 'path';
import fs from 'fs';

const DATA_DIR = path.join(__dirname, '../../data');

export const createPublicLink = async (req: Request, res: Response): Promise<void> => {
    try {
        const { username, filePath } = req.body;
        
        if (!username || !filePath) {
            res.status(400).json({ error: 'Thiếu username hoặc filePath' });
            return;
        }
        
        const pool = await sql.connect();
        const userResult = await pool.request()
            .input('username', sql.VarChar, username)
            .query('SELECT id FROM users WHERE username = @username');
            
        if (userResult.recordset.length === 0) {
            res.status(404).json({ error: 'Không tìm thấy người dùng' });
            return;
        }
        
        const ownerId = userResult.recordset[0].id;

        const token = randomUUID();
        
        const pool = await sql.connect();
        await pool.request()
            .input('token', sql.VarChar, token)
            .input('owner_id', sql.Int, ownerId)
            .input('file_path', sql.VarChar, filePath)
            .query(`
                INSERT INTO public_links (token, owner_id, file_path)
                VALUES (@token, @owner_id, @file_path)
            `);
            
        res.json({ token, message: 'Đã tạo link công khai thành công' });
    } catch (error: any) {
        console.error('Lỗi khi tạo public link:', error);
        res.status(500).json({ error: error.message });
    }
};

export const getPublicLinkInfo = async (req: Request, res: Response): Promise<void> => {
    try {
        const { token } = req.params;
        
        const pool = await sql.connect();
        const result = await pool.request()
            .input('token', sql.VarChar, token)
            .query(`
                SELECT p.file_path, u.username as owner_username
                FROM public_links p
                JOIN users u ON p.owner_id = u.id
                WHERE p.token = @token
            `);
            
        if (result.recordset.length === 0) {
            res.status(404).json({ error: 'Link không tồn tại hoặc đã hết hạn' });
            return;
        }
        
        const record = result.recordset[0];
        const filePath = record.file_path;
        const ownerUsername = record.owner_username;
        const absolutePath = path.join(DATA_DIR, filePath);
        
        if (!fs.existsSync(absolutePath)) {
            res.status(404).json({ error: 'Không tìm thấy file trên server' });
            return;
        }
        
        const stats = fs.statSync(absolutePath);
        const fileName = path.basename(filePath);
        
        res.json({
            fileName,
            ownerUsername,
            size: stats.size,
            filePath
        });
    } catch (error: any) {
        console.error('Lỗi khi lấy thông tin public link:', error);
        res.status(500).json({ error: error.message });
    }
};

export const downloadPublicLink = async (req: Request, res: Response): Promise<void> => {
    try {
        const { token } = req.params;
        
        const pool = await sql.connect();
        const result = await pool.request()
            .input('token', sql.VarChar, token)
            .query(`
                SELECT file_path
                FROM public_links
                WHERE token = @token
            `);
            
        if (result.recordset.length === 0) {
            res.status(404).json({ error: 'Link không tồn tại' });
            return;
        }
        
        const filePath = result.recordset[0].file_path;
        const absolutePath = path.join(DATA_DIR, filePath);
        
        if (!fs.existsSync(absolutePath)) {
            res.status(404).json({ error: 'File không tồn tại trên server' });
            return;
        }
        
        res.download(absolutePath);
    } catch (error: any) {
        console.error('Lỗi khi tải file qua public link:', error);
        res.status(500).json({ error: error.message });
    }
};
