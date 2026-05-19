import { Request, Response } from 'express';
import { connectDB } from '../config/database';
import sql from 'mssql';
import fs from 'fs';
import path from 'path';


const bcrypt = require('bcrypt')


export const createUser = async (req: Request, res: Response) => {
  try {
    const { username, password } = req.body;
    if (!username || !password) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username, password)' });
    }

    const hashedPassword = await bcrypt.hash(password, Number(process.env.BCRYPT_SALT_ROUNDS))
    const pool = await connectDB();
    const result = await pool.request()
      .input('username', sql.VarChar(255), username)
      .input('password', sql.VarChar(255), hashedPassword)
      .query(`
        INSERT INTO users (username, password)
        OUTPUT 
        INSERTED.id,
        INSERTED.username
        VALUES (
        @username,
        @password
        )
      `);

    const userFolderPath = path.join(__dirname, '../../data', username);
    const binUserFolderPath = path.join(__dirname, '../../bin', username);
    if (!fs.existsSync(userFolderPath)) {
      fs.mkdirSync(userFolderPath, { recursive: true });
    }

    if (!fs.existsSync(binUserFolderPath)) {
      fs.mkdirSync(binUserFolderPath, { recursive: true });
    }

    res.status(201).json({ message: 'Tạo thành công', data: result.recordset[0] });
  } catch (error: any) {
    if (error.number === 2627) {
      return res.status(400).json({ error: 'Tài khoản đã tồn tại' });
    }
    res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
  }
};

export const login = async (req: Request, res: Response) => {
  try {
    const { username, password } = req.body;
    if (!username || !password) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username, password)' });
    }

    const pool = await connectDB();
    const result = await pool.request()
      .input('username', sql.VarChar(255), username)
      .query(`SELECT * FROM users WHERE username = @username`);

    const user = result.recordset[0];
    if (!user) {
      return res.status(401).json({ error: 'Tài khoản hoặc mật khẩu không chính xác' });
    }

    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) {
      return res.status(401).json({ error: 'Tài khoản hoặc mật khẩu không chính xác' });
    }

    const { password: userPassword, ...userWithoutPassword } = user;

    res.status(200).json({ message: 'Đăng nhập thành công', data: userWithoutPassword });
  } catch (error: any) {
    res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
  }
};