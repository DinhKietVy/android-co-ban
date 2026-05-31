import jwt from 'jsonwebtoken';

export const generateAccessToken = (user: { id: any; account: string }): string => {
  return jwt.sign(
    { id: user.id, account: user.account },
    process.env.JWT_SECRET!,
    {
      expiresIn: (process.env.JWT_ACCESS_EXPIRES || '15m') as any
    }
  );
};

export const generateRefreshToken = (user: { id: any }): string => {
  return jwt.sign(
    { id: user.id },
    process.env.JWT_SECRET!,
    { expiresIn: (process.env.JWT_REFRESH_EXPIRES || '7d') as any }
  );
};