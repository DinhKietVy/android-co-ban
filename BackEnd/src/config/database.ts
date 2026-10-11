// === database.ts ===
import sql from 'mssql';
import dotenv from 'dotenv';

dotenv.config();

const dbConfig: sql.config = {
  user: process.env.DB_USER || 'myuser',
  password: process.env.DB_PASSWORD || '123456',
  server: process.env.DB_SERVER || 'localhost',
  database: process.env.DB_NAME || 'ANDROID',

  options: {
    encrypt: false, // true nếu dùng Azure
    trustServerCertificate: true,
  },

  pool: {
    max: 10,
    min: 0,
    idleTimeoutMillis: 30000,
  },

  connectionTimeout: 30000,
  requestTimeout: 30000,
};

// Singleton pool
let pool: sql.ConnectionPool;

export const connectDB = async (): Promise<sql.ConnectionPool> => {
  try {
    if (pool) return pool;

    pool = await sql.connect(dbConfig);

    console.log('✅ Kết nối SQL Server THÀNH CÔNG!');
    console.log(`   Server   : ${dbConfig.server}`);
    console.log(`   Database : ${dbConfig.database}`);
    console.log(`   Auth     : SQL Server Authentication`);

    // Auto-migration: Đảm bảo bảng share_file có cột permission
    try {
      await pool.request().query(`
        IF NOT EXISTS (
          SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_NAME = 'share_file' AND COLUMN_NAME = 'permission'
        )
        BEGIN
          ALTER TABLE share_file ADD permission VARCHAR(50) NOT NULL DEFAULT 'READ';
        END
      `);
      console.log('✅ Migration: Kiểm tra/cập nhật cột permission cho share_file thành công');
    } catch (migErr: any) {
      console.warn('⚠️ Auto-migration share_file permission warning:', migErr.message);
    }

    return pool;
  } catch (err: any) {
    console.error('❌ Kết nối SQL Server THẤT BẠI:', err.message);
    throw err;
  }
};

export default sql;