import { Request, Response } from 'express';
import fs from 'fs';
import path from 'path';
import multer from 'multer';

// Cấu hình storage cho multer
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    // LƯU Ý: Phía client (frontend) cần gửi 'username' và 'targetPath' TRƯỚC trường 'file' trong form-data
    // Nếu gửi file trước, req.body sẽ trống tại thời điểm này.
    const { username, targetPath } = req.body;
    
    if (!username) {
      return cb(new Error('Thiếu username trong request body (hoặc không được gửi trước file)'), '');
    }

    // Đường dẫn gốc của user
    const userRootPath = path.resolve(__dirname, '../../data', username);
    
    // Đường dẫn đích nơi muốn upload file (mặc định là thư mục gốc của user nếu không truyền)
    const absoluteTargetPath = path.resolve(userRootPath, targetPath || '');

    // Chống Path Traversal
    if (!absoluteTargetPath.startsWith(userRootPath)) {
      return cb(new Error('Đường dẫn upload không hợp lệ'), '');
    }
    
    // Kiểm tra và tạo thư mục nếu chưa tồn tại
    if (!fs.existsSync(absoluteTargetPath)) {
      fs.mkdirSync(absoluteTargetPath, { recursive: true });
    }

    cb(null, absoluteTargetPath);
  },
  filename: (req, file, cb) => {
    // Lưu file với thời gian để tránh trùng tên
    cb(null, Date.now() + '-' + file.originalname);
  }
});

// Khởi tạo middleware multer
const upload = multer({ storage }).single('file');

export const uploadData = (req: Request, res: Response) => {
  // Gọi hàm middleware upload bên trong controller để dễ dàng xử lý lỗi
  upload(req, res, function (err) {
    if (err instanceof multer.MulterError) {
      // Xảy ra lỗi từ phía multer
      return res.status(400).json({ error: 'Lỗi khi upload file', detail: err.message });
    } else if (err) {
      // Xảy ra lỗi không xác định (ví dụ thiếu username hoặc sai đường dẫn)
      return res.status(400).json({ error: err.message });
    }

    // Sau khi upload thành công, lấy thông tin
    const { username, targetPath } = req.body;
    const file = req.file;

    if (!username) {
      return res.status(400).json({ error: 'Thiếu username' });
    }

    if (!file) {
      return res.status(400).json({ error: 'Thiếu file upload' });
    }

    return res.status(200).json({
      message: 'Upload dữ liệu thành công',
      data: {
        username,
        targetPath: targetPath || '',
        filename: file.filename,
        originalName: file.originalname,
        path: file.path,
        size: file.size,
        mimetype: file.mimetype
      }
    });
  });
};

// API tạo folder
export const createFolder = (req: Request, res: Response) => {
  try {
    // targetPath: Đường dẫn của thư mục cha (nơi sẽ chứa folder mới)
    // folderName: Tên thư mục mới muốn tạo bên trong
    const { username, targetPath, folderName } = req.body;

    if (!username || !folderName || targetPath === undefined) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username, targetPath, folderName)' });
    }

    // Làm sạch tên folder mới để tránh ký tự đặc biệt
    const safeFolderName = folderName.replace(/[^a-zA-Z0-9_\-\s]/g, '');

    // Đường dẫn thư mục gốc của user
    const userRootPath = path.resolve(__dirname, '../../data', username);
    
    // Đường dẫn thư mục cha (nơi muốn tạo folder bên trong)
    // path.resolve sẽ tự động chuẩn hóa các dấu xuyệt và dọn dẹp đường dẫn
    const absoluteTargetPath = path.resolve(userRootPath, targetPath);

    // Chống Path Traversal: Đảm bảo đường dẫn đích phải nằm trong thư mục gốc của user
    if (!absoluteTargetPath.startsWith(userRootPath)) {
      return res.status(403).json({ error: 'Đường dẫn không hợp lệ' });
    }

    // YÊU CẦU: Đường dẫn thư mục cha phải tồn tại
    if (!fs.existsSync(absoluteTargetPath) || !fs.statSync(absoluteTargetPath).isDirectory()) {
      return res.status(404).json({ error: 'Đường dẫn thư mục cha không tồn tại' });
    }

    // Đường dẫn tuyệt đối của thư mục sẽ được tạo
    const folderPathToCreate = path.join(absoluteTargetPath, safeFolderName);

    // Kiểm tra xem folder mới này đã tồn tại chưa
    if (fs.existsSync(folderPathToCreate)) {
      return res.status(400).json({ error: 'Thư mục này đã tồn tại' });
    }

    // Tạo folder mới
    fs.mkdirSync(folderPathToCreate);

    return res.status(201).json({
      message: 'Tạo thư mục thành công',
      data: {
        username,
        targetPath,
        folderName: safeFolderName
      }
    });

  } catch (error: any) {
    return res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
  }
};

// API di chuyển file
export const moveFile = (req: Request, res: Response) => {
  try {
    const { username, sourceFilePath, targetFolderPath } = req.body;

    if (!username || sourceFilePath === undefined || targetFolderPath === undefined) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username, sourceFilePath, targetFolderPath)' });
    }

    const userRootPath = path.resolve(__dirname, '../../data', username);
    
    // Kiểm tra đường dẫn file nguồn
    const absoluteSourcePath = path.resolve(userRootPath, sourceFilePath);
    if (!absoluteSourcePath.startsWith(userRootPath)) {
      return res.status(403).json({ error: 'Đường dẫn file nguồn không hợp lệ' });
    }

    if (!fs.existsSync(absoluteSourcePath) || !fs.statSync(absoluteSourcePath).isFile()) {
      return res.status(404).json({ error: 'File nguồn không tồn tại' });
    }

    // Kiểm tra đường dẫn thư mục đích
    const absoluteTargetFolderPath = path.resolve(userRootPath, targetFolderPath);
    if (!absoluteTargetFolderPath.startsWith(userRootPath)) {
      return res.status(403).json({ error: 'Đường dẫn thư mục đích không hợp lệ' });
    }

    if (!fs.existsSync(absoluteTargetFolderPath) || !fs.statSync(absoluteTargetFolderPath).isDirectory()) {
      return res.status(404).json({ error: 'Thư mục đích không tồn tại' });
    }

    // Đường dẫn file mới
    const fileName = path.basename(absoluteSourcePath);
    const newFilePath = path.join(absoluteTargetFolderPath, fileName);

    // Kiểm tra trùng lặp tại thư mục đích
    if (fs.existsSync(newFilePath)) {
      return res.status(400).json({ error: 'Đã có file cùng tên tại thư mục đích' });
    }

    // Di chuyển file
    fs.renameSync(absoluteSourcePath, newFilePath);

    return res.status(200).json({
      message: 'Di chuyển file thành công',
      data: {
        username,
        fileName,
        from: sourceFilePath,
        to: targetFolderPath
      }
    });

  } catch (error: any) {
    return res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
  }
};

// API di chuyển thư mục
export const moveFolder = (req: Request, res: Response) => {
  try {
    const { username, sourceFolderPath, targetFolderPath } = req.body;

    if (!username || sourceFolderPath === undefined || targetFolderPath === undefined) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username, sourceFolderPath, targetFolderPath)' });
    }

    const userRootPath = path.resolve(__dirname, '../../data', username);
    
    // Kiểm tra đường dẫn thư mục nguồn
    const absoluteSourcePath = path.resolve(userRootPath, sourceFolderPath);
    if (!absoluteSourcePath.startsWith(userRootPath)) {
      return res.status(403).json({ error: 'Đường dẫn thư mục nguồn không hợp lệ' });
    }

    // Không cho phép di chuyển chính thư mục gốc của user
    if (absoluteSourcePath === userRootPath) {
      return res.status(400).json({ error: 'Không thể di chuyển thư mục gốc của người dùng' });
    }

    if (!fs.existsSync(absoluteSourcePath) || !fs.statSync(absoluteSourcePath).isDirectory()) {
      return res.status(404).json({ error: 'Thư mục nguồn không tồn tại' });
    }

    // Kiểm tra đường dẫn thư mục đích
    const absoluteTargetFolderPath = path.resolve(userRootPath, targetFolderPath);
    if (!absoluteTargetFolderPath.startsWith(userRootPath)) {
      return res.status(403).json({ error: 'Đường dẫn thư mục đích không hợp lệ' });
    }

    if (!fs.existsSync(absoluteTargetFolderPath) || !fs.statSync(absoluteTargetFolderPath).isDirectory()) {
      return res.status(404).json({ error: 'Thư mục đích không tồn tại' });
    }

    // Đảm bảo không di chuyển thư mục cha vào trong thư mục con của chính nó (vd: di chuyển A vào A/B)
    // Cần cẩn thận nếu targetFolderPath bằng sourceFolderPath hoặc bắt đầu bằng sourceFolderPath + separator
    if (absoluteTargetFolderPath === absoluteSourcePath || absoluteTargetFolderPath.startsWith(absoluteSourcePath + path.sep)) {
      return res.status(400).json({ error: 'Không thể di chuyển thư mục vào bên trong chính nó hoặc các thư mục con của nó' });
    }

    // Đường dẫn thư mục mới
    const folderName = path.basename(absoluteSourcePath);
    const newFolderPath = path.join(absoluteTargetFolderPath, folderName);

    // Kiểm tra trùng lặp tại thư mục đích
    if (fs.existsSync(newFolderPath)) {
      return res.status(400).json({ error: 'Đã có thư mục hoặc file cùng tên tại vị trí đích' });
    }

    // Di chuyển thư mục
    // renameSync hoạt động tốt cho cả di chuyển file và folder trên cùng một ổ đĩa
    fs.renameSync(absoluteSourcePath, newFolderPath);

    return res.status(200).json({
      message: 'Di chuyển thư mục thành công',
      data: {
        username,
        folderName,
        from: sourceFolderPath,
        to: targetFolderPath
      }
    });

  } catch (error: any) {
    return res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
  }
};

// API xoá file
export const deleteFile = (req: Request, res: Response) => {
  try {
    const { username, filePath } = req.body;

    if (!username || filePath === undefined) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username, filePath)' });
    }

    const userRootPath = path.resolve(__dirname, '../../data', username);
    
    // Kiểm tra đường dẫn file
    const absoluteFilePath = path.resolve(userRootPath, filePath);

    // Chống Path Traversal
    if (!absoluteFilePath.startsWith(userRootPath)) {
      return res.status(403).json({ error: 'Đường dẫn file không hợp lệ' });
    }

    if (!fs.existsSync(absoluteFilePath)) {
      return res.status(404).json({ error: 'File không tồn tại' });
    }

    // Đảm bảo đây là file, không phải thư mục
    if (!fs.statSync(absoluteFilePath).isFile()) {
      return res.status(400).json({ error: 'Đường dẫn yêu cầu không trỏ tới một file' });
    }

    // Xoá file
    fs.unlinkSync(absoluteFilePath);

    return res.status(200).json({
      message: 'Xoá file thành công',
      data: {
        username,
        filePath
      }
    });

  } catch (error: any) {
    return res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
  }
};

// API xoá thư mục (xoá đệ quy toàn bộ thư mục con và file bên trong)
export const deleteFolder = (req: Request, res: Response) => {
  try {
    const { username, folderPath } = req.body;

    if (!username || folderPath === undefined) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username, folderPath)' });
    }

    const userRootPath = path.resolve(__dirname, '../../data', username);
    
    // Kiểm tra đường dẫn thư mục
    const absoluteFolderPath = path.resolve(userRootPath, folderPath);

    // Chống Path Traversal
    if (!absoluteFolderPath.startsWith(userRootPath)) {
      return res.status(403).json({ error: 'Đường dẫn thư mục không hợp lệ' });
    }

    // KHÔNG cho phép xoá chính thư mục gốc của user
    if (absoluteFolderPath === userRootPath) {
      return res.status(400).json({ error: 'Không được phép xoá thư mục gốc của người dùng' });
    }

    if (!fs.existsSync(absoluteFolderPath)) {
      return res.status(404).json({ error: 'Thư mục không tồn tại' });
    }

    // Đảm bảo đây là thư mục, không phải file
    if (!fs.statSync(absoluteFolderPath).isDirectory()) {
      return res.status(400).json({ error: 'Đường dẫn yêu cầu không trỏ tới một thư mục' });
    }

    // Xoá thư mục cùng toàn bộ file/folder con bên trong
    fs.rmSync(absoluteFolderPath, { recursive: true, force: true });

    return res.status(200).json({
      message: 'Xoá thư mục thành công',
      data: {
        username,
        folderPath
      }
    });

  } catch (error: any) {
    return res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
  }
};

// API lấy danh sách dữ liệu (files và thư mục con)
export const listDirectory = (req: Request, res: Response) => {
  try {
    const { username, folderPath } = req.body;

    if (!username || folderPath === undefined) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username, folderPath)' });
    }

    const userRootPath = path.resolve(__dirname, '../../data', username);
    const absoluteFolderPath = path.resolve(userRootPath, folderPath);

    // Chống Path Traversal
    if (!absoluteFolderPath.startsWith(userRootPath)) {
      return res.status(403).json({ error: 'Đường dẫn thư mục không hợp lệ' });
    }

    if (!fs.existsSync(absoluteFolderPath)) {
      return res.status(404).json({ error: 'Thư mục không tồn tại' });
    }

    if (!fs.statSync(absoluteFolderPath).isDirectory()) {
      return res.status(400).json({ error: 'Đường dẫn yêu cầu không trỏ tới một thư mục' });
    }

    // Đọc danh sách các mục bên trong thư mục
    const items = fs.readdirSync(absoluteFolderPath, { withFileTypes: true });

    const folders: any[] = [];
    const files: any[] = [];

    for (const item of items) {
      const itemPath = path.join(absoluteFolderPath, item.name);
      try {
        const stats = fs.statSync(itemPath);

        if (item.isDirectory()) {
          folders.push({
            name: item.name,
            createdAt: stats.birthtime,
            modifiedAt: stats.mtime
          });
        } else if (item.isFile()) {
          files.push({
            name: item.name,
            size: stats.size, // Kích thước tính bằng byte
            createdAt: stats.birthtime,
            modifiedAt: stats.mtime
          });
        }
      } catch (err) {
        // Bỏ qua nếu có lỗi không đọc được stat của một file nào đó
      }
    }

    return res.status(200).json({
      message: 'Lấy dữ liệu thành công',
      data: {
        username,
        currentFolder: folderPath,
        folders,
        files
      }
    });

  } catch (error: any) {
    return res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
  }
};