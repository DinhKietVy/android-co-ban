import { Request, Response } from 'express';
import { connectDB } from '../config/database';
import sql from 'mssql';
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';

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
