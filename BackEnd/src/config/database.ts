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

    return pool;
  } catch (err: any) {
    console.error('❌ Kết nối SQL Server THẤT BẠI:', err.message);
    throw err;
  }
};

export default sql;