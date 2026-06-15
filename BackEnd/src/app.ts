import express from 'express';
import cors from 'cors';
import { connectDB } from './config/database';

import routes from './routes/index.routes';
import cookieParser from 'cookie-parser';

import { setupSwagger } from "./config/swagger";


const app = express();
const PORT = process.env.PORT || 5000;

app.use(cookieParser())
app.use(cors({
  origin: true, // Cho phép tất cả các origin trong lúc dev bằng ngrok
  credentials: true,
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'ngrok-skip-browser-warning'],
}));

app.use(express.json());

// Test kết nối database
app.get('/test-db', async (req, res) => {
  try {
    const pool = await connectDB();

    const result = await pool
      .request()
      .query('SELECT @@VERSION as version');

    res.json({
      message: 'Kết nối database thành công!',
      sqlServerVersion: result.recordset[0].version
    });

  } catch (error: any) {
    console.error('❌ Test DB Error:', error.message);

    res.status(500).json({
      error: 'Lỗi kết nối database',
      detail: error.message // 👉 giúp debug
    });
  }
});

routes(app)

setupSwagger(app);


app.get('/', (req, res) => {
  res.send('🚀 Express + TypeScript Server đang chạy!');
});

app.listen(PORT, () => {
  console.log(`🚀 Server chạy tại: http://localhost:${PORT}`);
});

export default app;