import { Router } from 'express';
import { createUser, googleAuth, login } from '../controllers/user.controller';

const router = Router();

router.post('/', createUser);
router.post('/google-auth', googleAuth);
router.post('/login', login);

export default router;
