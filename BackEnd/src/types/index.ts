import { Request } from 'express';

export interface AuthRequest extends Request {
    user?: {
        id: string | number;
        account: string;
    };
}