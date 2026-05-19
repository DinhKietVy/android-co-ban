import { Request, Response } from 'express';
import fs from 'fs';
import path from 'path';

// API lấy danh sách file trong thùng rác
export const listBinFiles = (req: Request, res: Response) => {
  try {
    const { username } = req.body;

    if (!username) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username)' });
    }

    // Đường dẫn tới thùng rác của user
    const binUserPath = path.resolve(__dirname, '../../bin', username);

    // Kiểm tra xem thư mục thùng rác đã tồn tại chưa
    if (!fs.existsSync(binUserPath)) {
      // Nếu chưa tồn tại, trả về danh sách rỗng
      return res.status(200).json({
        message: 'Lấy dữ liệu thành công',
        data: {
          username,
          files: []
        }
      });
    }

    // Đọc danh sách file trong thùng rác
    const items = fs.readdirSync(binUserPath, { withFileTypes: true });
    const files: any[] = [];

    for (const item of items) {
      if (item.isFile()) {
        const itemPath = path.join(binUserPath, item.name);
        try {
          const stats = fs.statSync(itemPath);
          files.push({
            name: item.name,
            size: stats.size, // Kích thước byte
            deletedAt: stats.mtime, // Thời gian di chuyển vào bin
          });
        } catch (err) {
          // Bỏ qua lỗi nếu không đọc được stat của 1 file cụ thể
        }
      }
    }

    return res.status(200).json({
      message: 'Lấy dữ liệu thành công',
      data: {
        username,
        files
      }
    });

  } catch (error: any) {
    return res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
  }
};

// API khôi phục file từ thùng rác vào thư mục recover
export const restoreFile = (req: Request, res: Response) => {
  try {
    const { username, fileName } = req.body;

    if (!username || !fileName) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username, fileName)' });
    }

    const binUserPath = path.resolve(__dirname, '../../bin', username);
    const absoluteFilePath = path.resolve(binUserPath, fileName);

    // Chống Path Traversal
    if (!absoluteFilePath.startsWith(binUserPath)) {
      return res.status(403).json({ error: 'Đường dẫn file không hợp lệ' });
    }

    if (!fs.existsSync(absoluteFilePath)) {
      return res.status(404).json({ error: 'File trong thùng rác không tồn tại' });
    }

    // Đảm bảo đây là file
    if (!fs.statSync(absoluteFilePath).isFile()) {
      return res.status(400).json({ error: 'Đường dẫn yêu cầu không trỏ tới một file' });
    }

    // Thư mục đích: data/username/recover
    const userRootPath = path.resolve(__dirname, '../../data', username);
    const recoverFolderPath = path.join(userRootPath, 'recover');

    // Tạo thư mục recover nếu chưa có
    if (!fs.existsSync(recoverFolderPath)) {
      fs.mkdirSync(recoverFolderPath, { recursive: true });
    }

    // Lọc bỏ tiền tố timestamp (13 số + dấu gạch ngang) nếu có do mình đã thêm lúc xoá
    const originalName = fileName.replace(/^\d{13}-/, '');
    let newFilePath = path.join(recoverFolderPath, originalName);

    // Xử lý trùng lặp tên file trong thư mục recover
    if (fs.existsSync(newFilePath)) {
      // Nếu trùng tên trong thư mục recover, sẽ giữ lại 1 timestamp mới để không bị ghi đè
      newFilePath = path.join(recoverFolderPath, `${Date.now()}-${originalName}`);
    }

    // Di chuyển file từ thùng rác sang thư mục recover
    fs.renameSync(absoluteFilePath, newFilePath);

    return res.status(200).json({
      message: 'Khôi phục file thành công',
      data: {
        username,
        recoveredTo: `recover/${path.basename(newFilePath)}`
      }
    });

  } catch (error: any) {
    return res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
  }
};

// API xoá vĩnh viễn 1 file trong thùng rác
export const deleteBinFile = (req: Request, res: Response) => {
  try {
    const { username, fileName } = req.body;

    if (!username || !fileName) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username, fileName)' });
    }

    const binUserPath = path.resolve(__dirname, '../../bin', username);
    const absoluteFilePath = path.resolve(binUserPath, fileName);

    // Chống Path Traversal
    if (!absoluteFilePath.startsWith(binUserPath)) {
      return res.status(403).json({ error: 'Đường dẫn file không hợp lệ' });
    }

    if (!fs.existsSync(absoluteFilePath)) {
      return res.status(404).json({ error: 'File trong thùng rác không tồn tại' });
    }

    // Đảm bảo đây là file
    if (!fs.statSync(absoluteFilePath).isFile()) {
      return res.status(400).json({ error: 'Đường dẫn yêu cầu không trỏ tới một file' });
    }

    // Xoá vĩnh viễn file
    fs.unlinkSync(absoluteFilePath);

    return res.status(200).json({
      message: 'Xoá vĩnh viễn file thành công',
      data: {
        username,
        fileName
      }
    });

  } catch (error: any) {
    return res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
  }
};