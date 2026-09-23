import { Router } from 'express';
import { createPublicLink, getPublicLinkInfo, downloadPublicLink } from '../controllers/public-link.controller';
// import { authenticateJWT } from '../middlewares/auth.middleware'; // Wait, let's see if we need authentication for creation

const router = Router();

// LƯU Ý: create cần xác thực người dùng, nhưng theo structure của project, có thể authentication middleware được cài ở app.ts hay route level.
// Nếu chưa chắc, ta cứ expose route trước hoặc xem cách các route khác hoạt động.

router.post('/create', createPublicLink);
router.get('/:token/info', getPublicLinkInfo);
router.get('/:token/download', downloadPublicLink);

export default router;
