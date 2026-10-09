import { Router } from 'express';
import { createPublicLink, getPublicLinkInfo, downloadPublicLink, deletePublicLink } from '../controllers/public-link.controller';

const router = Router();

router.post('/create', createPublicLink);
router.post('/delete', deletePublicLink);
router.get('/:token/info', getPublicLinkInfo);
router.get('/:token/download', downloadPublicLink);

export default router;
