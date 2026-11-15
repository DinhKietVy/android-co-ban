import { Router, raw } from 'express';
import { createHash, createHmac, randomUUID } from 'crypto';
import { authenticateToken } from '../middleware/auth';
import { AuthRequest } from '../types';
import { connectDB } from '../config/database';

const router = Router();
router.use(authenticateToken);
router.use(raw({ type: 'multipart/form-data', limit: '12mb' }));
router.use(async (req: AuthRequest, res) => {
    const path = req.url;
    const allowed = /^(?:\/api\/ai\/(?:chat|search|dlp\/scan|sessions(?:\/[^/?]+(?:\/(?:messages|title))?)?)|\/(?:predict-image|predict-yolo|search-images-by-text))$/;
    if (!allowed.test(path) || !['GET', 'POST', 'DELETE', 'PATCH'].includes(req.method)) {
        res.sendStatus(404); return;
    }
    try {
        const secret = process.env.AI_INTERNAL_SECRET || '';
        if (secret.length < 32) throw new Error('Missing internal secret');
        const base = new URL(process.env.AI_INTERNAL_URL || 'http://127.0.0.1:8000');
        if (base.protocol !== 'https:' && !(base.protocol === 'http:' && ['127.0.0.1', '[::1]'].includes(base.hostname))) {
            throw new Error('Internal AI requires HTTPS');
        }
        const pool = await connectDB();
        const result = await pool.request().input('id', req.user!.id)
            .query('SELECT username FROM users WHERE id = @id');
        const username = result.recordset[0]?.username;
        if (!username) { res.sendStatus(403); return; }
        const user = encodeURIComponent(username);
        let body: Buffer | undefined;
        const contentType = req.headers['content-type'] || 'application/json';
        if (!['GET', 'HEAD'].includes(req.method)) {
            body = Buffer.isBuffer(req.body) ? req.body : Buffer.from(JSON.stringify({ ...req.body, username }));
        }
        const timestamp = Math.floor(Date.now() / 1000).toString();
        const nonce = randomUUID();
        const digest = createHash('sha256').update(body || Buffer.alloc(0)).digest('hex');
        const signature = createHmac('sha256', secret)
            .update([req.method, path, timestamp, nonce, user, digest].join('\n')).digest('hex');
        const response = await fetch(new URL(path, base), {
            method: req.method, body: body ? new Uint8Array(body).buffer : undefined, redirect: 'error', signal: AbortSignal.timeout(60000),
            headers: { 'Content-Type': contentType, 'X-AI-User': user,
                'X-AI-Timestamp': timestamp, 'X-AI-Nonce': nonce, 'X-AI-Signature': signature },
        });
        res.status(response.status).type(response.headers.get('content-type') || 'application/json')
            .send(Buffer.from(await response.arrayBuffer()));
    } catch {
        res.status(503).json({ success: false, error: 'Dịch vụ AI tạm thời không khả dụng' });
    }
});
export default router;
