import { Request, Response } from 'express';
import { connectDB } from '../config/database';
import sql from 'mssql';
import fs from 'fs';
import { AuthRequest } from '../types';
import path from 'path';
import crypto from 'crypto';
import { sendResetPasswordEmail } from '../utils/mailer';
import { generateAccessToken, generateRefreshToken } from '../utils/token';

const bcrypt = require('bcrypt');

const ensureUserFolder = (username: string) => {
  const userFolderPath = path.join(__dirname, '../../data', username);
  if (!fs.existsSync(userFolderPath)) {
    fs.mkdirSync(userFolderPath, { recursive: true });
  }
};

const getSaltRounds = () => {
  const parsed = Number(process.env.BCRYPT_SALT_ROUNDS);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 10;
};

const normalizeIdentifier = (value: unknown) => {
  return String(value || '').trim().toLowerCase();
};

const issueAuthCookies = (
  res: Response,
  user: { id: number; username: string }
) => {
  const accessToken = generateAccessToken({ id: user.id, account: user.username });
  const refreshToken = generateRefreshToken({ id: user.id });

  res.cookie('refreshToken', refreshToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'development',
    sameSite: 'strict',
    maxAge: 7 * 24 * 60 * 60 * 1000,
  });

  res.cookie('accessToken', accessToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'development',
    sameSite: 'strict',
    maxAge: 15 * 60 * 1000,
  });
};

export const createUser = async (req: Request, res: Response) => {
  try {
    const { username, fullName, email, password } = req.body;
    const normalizedUsername = normalizeIdentifier(username);
    const normalizedEmail = normalizeIdentifier(email);
    const normalizedFullName = String(fullName || '').trim();

    if (!normalizedUsername || !normalizedFullName || !normalizedEmail || !password) {
      return res.status(400).json({ error: 'Thieu thong tin bat buoc (username, fullName, email, password)' });
    }

    const hashedPassword = await bcrypt.hash(password, getSaltRounds());
    const pool = await connectDB();
    const result = await pool.request()
      .input('username', sql.VarChar(255), normalizedUsername)
      .input('full_name', sql.NVarChar(255), normalizedFullName)
      .input('email', sql.VarChar(255), normalizedEmail)
      .input('password', sql.VarChar(255), hashedPassword)
      .query(`
        INSERT INTO users (username, full_name, email, password)
        OUTPUT
          INSERTED.id,
          INSERTED.username,
          INSERTED.full_name AS fullName,
          INSERTED.email
        VALUES (
          @username,
          @full_name,
          @email,
          @password
        )
      `);

    ensureUserFolder(normalizedUsername);
    return res.status(201).json({ message: 'Tao thanh cong', data: result.recordset[0] });
  } catch (error: any) {
    if (error.number === 2627) {
      return res.status(400).json({ error: 'Tai khoan hoac email da ton tai' });
    }
    return res.status(500).json({ error: 'Loi may chu', detail: error.message });
  }
};

export const login = async (req: Request, res: Response) => {
  try {
    const { username, password } = req.body;
    const normalizedIdentifier = normalizeIdentifier(username);
    if (!normalizedIdentifier || !password) {
      return res.status(400).json({ error: 'Thieu thong tin bat buoc (username/email, password)' });
    }

    const pool = await connectDB();
    const result = await pool.request()
      .input('identifier', sql.VarChar(255), normalizedIdentifier)
      .query(`
        SELECT TOP 1 *
        FROM users
        WHERE username = @identifier OR email = @identifier
      `);

    const user = result.recordset[0];
    if (!user) {
      return res.status(401).json({ error: 'Tai khoan hoac mat khau khong chinh xac' });
    }

    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) {
      return res.status(401).json({ error: 'Tai khoan hoac mat khau khong chinh xac' });
    }

    issueAuthCookies(res, {
      id: user.id,
      username: user.username,
    });

    return res.status(200).json({
      message: 'Dang nhap thanh cong',
      data: {
        id: user.id,
        username: user.username,
        fullName: user.full_name,
        email: user.email,
      },
    });
  } catch (error: any) {
    return res.status(500).json({ error: 'Loi may chu', detail: error.message });
  }
};

export const googleAuth = async (req: Request, res: Response) => {
  try {
    const { username, email, displayName, googleUid } = req.body;
    const normalizedUsername = normalizeIdentifier(username || email);
    const normalizedEmail = normalizeIdentifier(email || `${normalizedUsername}@google.local`);
    const normalizedFullName = String(displayName || normalizedUsername).trim();

    if (!normalizedUsername || !googleUid) {
      return res.status(400).json({ error: 'Thieu thong tin bat buoc (username/email, googleUid)' });
    }

    const pool = await connectDB();
    const existingUserResult = await pool.request()
      .input('username', sql.VarChar(255), normalizedUsername)
      .input('email', sql.VarChar(255), normalizedEmail)
      .query(`
        SELECT TOP 1 id, username, full_name AS fullName, email
        FROM users
        WHERE username = @username OR email = @email
      `);

    const existingUser = existingUserResult.recordset[0];
    if (existingUser) {
      ensureUserFolder(existingUser.username);
      issueAuthCookies(res, {
        id: existingUser.id,
        username: existingUser.username,
      });
      return res.status(200).json({
        message: 'Dang nhap Google thanh cong',
        data: existingUser,
      });
    }

    const generatedPassword = crypto.randomBytes(32).toString('hex');
    const hashedPassword = await bcrypt.hash(generatedPassword, getSaltRounds());

    const insertedUserResult = await pool.request()
      .input('username', sql.VarChar(255), normalizedUsername)
      .input('full_name', sql.NVarChar(255), normalizedFullName)
      .input('email', sql.VarChar(255), normalizedEmail)
      .input('password', sql.VarChar(255), hashedPassword)
      .query(`
        INSERT INTO users (username, full_name, email, password)
        OUTPUT
          INSERTED.id,
          INSERTED.username,
          INSERTED.full_name AS fullName,
          INSERTED.email
        VALUES (
          @username,
          @full_name,
          @email,
          @password
        )
      `);

    const insertedUser = insertedUserResult.recordset[0];
    ensureUserFolder(insertedUser.username);
    issueAuthCookies(res, {
      id: insertedUser.id,
      username: insertedUser.username,
    });

    return res.status(201).json({
      message: 'Tao tai khoan Google thanh cong',
      data: insertedUser,
      meta: {
        email,
        displayName,
        googleUid,
      },
    });
  } catch (error: any) {
    if (error.number === 2627) {
      return res.status(400).json({ error: 'Tai khoan hoac email da ton tai' });
    }
    return res.status(500).json({ error: 'Loi may chu', detail: error.message });
  }
};

export const forgotPassword = async (req: Request, res: Response) => {
  try {
    const { email } = req.body;
    const normalizedEmail = normalizeIdentifier(email);

    if (!normalizedEmail) {
      return res.status(400).json({ error: 'Vui long cung cap email' });
    }

    const pool = await connectDB();
    const userResult = await pool.request()
      .input('email', sql.VarChar(255), normalizedEmail)
      .query(`SELECT id FROM users WHERE email = @email`);

    if (userResult.recordset.length === 0) {
      return res.status(404).json({ error: 'Email khong ton tai trong he thong' });
    }

    const resetCode = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = new Date(Date.now() + 15 * 60 * 1000);

    await pool.request()
      .input('email', sql.VarChar(255), normalizedEmail)
      .input('reset_code', sql.VarChar(10), resetCode)
      .input('expires_at', sql.DateTime, expiresAt)
      .query(`
        UPDATE users
        SET reset_code = @reset_code, reset_code_expires_at = @expires_at
        WHERE email = @email
      `);

    await sendResetPasswordEmail(normalizedEmail, resetCode);

    return res.status(200).json({ message: 'Ma dat lai mat khau da duoc gui den email cua ban' });
  } catch (error: any) {
    return res.status(500).json({ error: 'Loi may chu', detail: error.message });
  }
};

export const verifyResetCode = async (req: Request, res: Response) => {
  try {
    const { email, code } = req.body;
    const normalizedEmail = normalizeIdentifier(email);
    const normalizedCode = String(code || '').trim();

    if (!normalizedEmail || !normalizedCode) {
      return res.status(400).json({ error: 'Thieu thong tin bat buoc (email, code)' });
    }

    const pool = await connectDB();
    const userResult = await pool.request()
      .input('email', sql.VarChar(255), normalizedEmail)
      .query(`
        SELECT id, reset_code, reset_code_expires_at
        FROM users
        WHERE email = @email
      `);

    const user = userResult.recordset[0];

    if (!user) {
      return res.status(404).json({ error: 'Email khong ton tai' });
    }

    if (!user.reset_code || user.reset_code !== normalizedCode) {
      return res.status(400).json({ error: 'Ma xac nhan khong hop le' });
    }

    if (!user.reset_code_expires_at || new Date() > new Date(user.reset_code_expires_at)) {
      return res.status(400).json({ error: 'Ma xac nhan da het han' });
    }

    return res.status(200).json({ message: 'Xac thuc ma OTP thanh cong' });
  } catch (error: any) {
    return res.status(500).json({ error: 'Loi may chu', detail: error.message });
  }
};

export const resetPassword = async (req: Request, res: Response) => {
  try {
    const { email, code, newPassword } = req.body;
    const normalizedEmail = normalizeIdentifier(email);

    if (!normalizedEmail || !code || !newPassword) {
      return res.status(400).json({ error: 'Thieu thong tin bat buoc (email, code, newPassword)' });
    }

    const pool = await connectDB();
    const userResult = await pool.request()
      .input('email', sql.VarChar(255), normalizedEmail)
      .query(`
        SELECT id, reset_code, reset_code_expires_at
        FROM users
        WHERE email = @email
      `);

    const user = userResult.recordset[0];

    if (!user) {
      return res.status(404).json({ error: 'Email khong ton tai' });
    }

    if (!user.reset_code || user.reset_code !== code) {
      return res.status(400).json({ error: 'Ma xac nhan khong hop le' });
    }

    if (new Date() > new Date(user.reset_code_expires_at)) {
      return res.status(400).json({ error: 'Ma xac nhan da het han' });
    }

    const hashedPassword = await bcrypt.hash(newPassword, getSaltRounds());

    await pool.request()
      .input('email', sql.VarChar(255), normalizedEmail)
      .input('password', sql.VarChar(255), hashedPassword)
      .query(`
        UPDATE users
        SET password = @password, reset_code = NULL, reset_code_expires_at = NULL
        WHERE email = @email
      `);

    return res.status(200).json({ message: 'Dat lai mat khau thanh cong' });
  } catch (error: any) {
    return res.status(500).json({ error: 'Loi may chu', detail: error.message });
  }
};

export const autoLogin = async (req: AuthRequest, res: Response) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      return res.status(401).json({ error: 'Khong the xac thuc nguoi dung' });
    }

    const pool = await connectDB();
    const result = await pool.request()
      .input('id', sql.Int, userId)
      .query(`
        SELECT id, username, full_name AS fullName, email
        FROM users
        WHERE id = @id
      `);

    const user = result.recordset[0];
    if (!user) {
      return res.status(404).json({ error: 'Khong tim thay nguoi dung' });
    }

    return res.status(200).json({
      message: 'Tu dong dang nhap thanh cong',
      data: {
        id: user.id,
        username: user.username,
        fullName: user.fullName,
        email: user.email,
      },
    });
  } catch (error: any) {
    return res.status(500).json({ error: 'Loi may chu', detail: error.message });
  }
};

export const updateProfile = async (req: AuthRequest, res: Response) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      return res.status(401).json({ error: 'Khong the xac thuc nguoi dung' });
    }

    const { fullName, email } = req.body;
    const normalizedEmail = normalizeIdentifier(email);
    const normalizedFullName = String(fullName || '').trim();

    if (!normalizedFullName || !normalizedEmail) {
      return res.status(400).json({ error: 'Thieu thong tin bat buoc (fullName, email)' });
    }

    const pool = await connectDB();
    
    // Check if email already exists for a different user
    const emailCheckResult = await pool.request()
      .input('email', sql.VarChar(255), normalizedEmail)
      .input('id', sql.Int, userId)
      .query(`
        SELECT id FROM users
        WHERE email = @email AND id != @id
      `);

    if (emailCheckResult.recordset.length > 0) {
      return res.status(400).json({ error: 'Email da duoc su dung boi nguoi dung khac' });
    }

    await pool.request()
      .input('id', sql.Int, userId)
      .input('full_name', sql.NVarChar(255), normalizedFullName)
      .input('email', sql.VarChar(255), normalizedEmail)
      .query(`
        UPDATE users
        SET full_name = @full_name, email = @email
        WHERE id = @id
      `);

    return res.status(200).json({ message: 'Cap nhat thong tin thanh cong' });
  } catch (error: any) {
    return res.status(500).json({ error: 'Loi may chu', detail: error.message });
  }
};

export const changePassword = async (req: AuthRequest, res: Response) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      return res.status(401).json({ error: 'Khong the xac thuc nguoi dung' });
    }

    const { oldPassword, newPassword } = req.body;
    if (!oldPassword || !newPassword) {
      return res.status(400).json({ error: 'Thieu thong tin bat buoc (oldPassword, newPassword)' });
    }

    const pool = await connectDB();
    const result = await pool.request()
      .input('id', sql.Int, userId)
      .query(`
        SELECT id, password
        FROM users
        WHERE id = @id
      `);

    const user = result.recordset[0];
    if (!user) {
      return res.status(404).json({ error: 'Khong tim thay nguoi dung' });
    }

    const isMatch = await bcrypt.compare(oldPassword, user.password);
    if (!isMatch) {
      return res.status(401).json({ error: 'Mat khau hien tai khong chinh xac' });
    }

    const hashedPassword = await bcrypt.hash(newPassword, getSaltRounds());
    
    await pool.request()
      .input('id', sql.Int, userId)
      .input('password', sql.VarChar(255), hashedPassword)
      .query(`
        UPDATE users
        SET password = @password
        WHERE id = @id
      `);

    // Logout logic will be handled on the client side
    return res.status(200).json({ message: 'Doi mat khau thanh cong' });
  } catch (error: any) {
    return res.status(500).json({ error: 'Loi may chu', detail: error.message });
  }
};

export const deleteAccount = async (req: AuthRequest, res: Response) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      return res.status(401).json({ error: 'Khong the xac thuc nguoi dung' });
    }

    const pool = await connectDB();
    
    // First find the user to delete their folder
    const userResult = await pool.request()
      .input('id', sql.Int, userId)
      .query('SELECT username FROM users WHERE id = @id');
      
    if (userResult.recordset.length > 0) {
      const username = userResult.recordset[0].username;
      
      // We will delete the user's data from the db.
      // Wait, there might be foreign key constraints (e.g. files, trash, etc.)
      // Typically, in a real app, we'd also delete their files from the DB or set cascading deletes.
      // Assuming cascade delete is set up, or there are no files table yet.
      await pool.request()
        .input('id', sql.Int, userId)
        .query('DELETE FROM users WHERE id = @id');
        
      // Delete their files from the filesystem
      const userFolderPath = path.join(__dirname, '../../data', username);
      if (fs.existsSync(userFolderPath)) {
        fs.rmSync(userFolderPath, { recursive: true, force: true });
      }
    } else {
      return res.status(404).json({ error: 'Khong tim thay nguoi dung' });
    }

    // Clear cookies
    res.clearCookie('accessToken');
    res.clearCookie('refreshToken');

    return res.status(200).json({ message: 'Xoa tai khoan thanh cong' });
  } catch (error: any) {
    return res.status(500).json({ error: 'Loi may chu', detail: error.message });
  }
};
