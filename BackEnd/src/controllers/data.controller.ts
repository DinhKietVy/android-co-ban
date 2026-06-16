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

    // Lưu lại đường dẫn để dùng ở hàm filename
    (req as any).uploadDestination = absoluteTargetPath;

    cb(null, absoluteTargetPath);
  },
  filename: (req, file, cb) => {
    const absoluteTargetPath = (req as any).uploadDestination;
    let finalName = file.originalname;
    
    if (absoluteTargetPath) {
      let counter = 1;
      const ext = path.extname(file.originalname);
      const baseName = path.basename(file.originalname, ext);
      
      while (fs.existsSync(path.join(absoluteTargetPath, finalName))) {
        finalName = `${baseName} (${counter})${ext}`;
        counter++;
      }
    } else {
      // Fallback nếu không có absoluteTargetPath
      finalName = Date.now() + '-' + file.originalname;
    }
    
    cb(null, finalName);
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

    // Đưa file vào thư mục bin/username thay vì xoá vĩnh viễn
    const binUserPath = path.resolve(__dirname, '../../bin', username);
    if (!fs.existsSync(binUserPath)) {
      fs.mkdirSync(binUserPath, { recursive: true });
    }

    const fileName = path.basename(absoluteFilePath);
    const newFilePath = path.join(binUserPath, `${Date.now()}-${fileName}`);

    fs.renameSync(absoluteFilePath, newFilePath);

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

    // Đưa toàn bộ file trong thư mục (và các thư mục con) vào thùng rác
    const binUserPath = path.resolve(__dirname, '../../bin', username);
    if (!fs.existsSync(binUserPath)) {
      fs.mkdirSync(binUserPath, { recursive: true });
    }

    // Hàm đệ quy duyệt qua các file và di chuyển
    const moveFilesToBin = (dirPath: string) => {
      const items = fs.readdirSync(dirPath, { withFileTypes: true });
      for (const item of items) {
        const itemPath = path.join(dirPath, item.name);
        if (item.isDirectory()) {
          moveFilesToBin(itemPath); // Đệ quy vào thư mục con
        } else if (item.isFile()) {
          const newFilePath = path.join(binUserPath, `${Date.now()}-${item.name}`);
          fs.renameSync(itemPath, newFilePath);
        }
      }
    };

    // Thực hiện di chuyển tất cả file
    moveFilesToBin(absoluteFolderPath);

    // Xoá cấu trúc thư mục (bây giờ chỉ còn các thư mục con rỗng)
    fs.rmSync(absoluteFolderPath, { recursive: true, force: true });

    return res.status(200).json({
      message: 'Đưa toàn bộ file trong thư mục vào thùng rác và xoá thư mục thành công',
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

// API tải file
export const downloadFile = (req: Request, res: Response) => {
  try {
    // Lấy dữ liệu từ query (nếu là GET request) hoặc body (nếu là POST request)
    const username = req.query.username || req.body.username;
    const filePath = req.query.filePath || req.body.filePath;

    if (!username || filePath === undefined) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username, filePath)' });
    }

    const userRootPath = path.resolve(__dirname, '../../data', username as string);
    
    // Kiểm tra đường dẫn file
    const absoluteFilePath = path.resolve(userRootPath, filePath as string);

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

    // Tải file về máy người dùng
    return res.download(absoluteFilePath, path.basename(absoluteFilePath), (err) => {
      if (err) {
        console.error('Lỗi khi tải file:', err);
        if (!res.headersSent) {
          res.status(500).json({ error: 'Lỗi trong quá trình truyền file' });
        }
      }
    });

  } catch (error: any) {
    if (!res.headersSent) {
      return res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
    }
  }
};

// API đổi tên file
export const renameFile = (req: Request, res: Response) => {
  try {
    const { username, filePath, newFileName } = req.body;

    if (!username || !filePath || !newFileName) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username, filePath, newFileName)' });
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

    // Đảm bảo đây là file
    if (!fs.statSync(absoluteFilePath).isFile()) {
      return res.status(400).json({ error: 'Đường dẫn yêu cầu không trỏ tới một file' });
    }

    // Lấy thư mục chứa file hiện tại và tạo đường dẫn mới
    const directoryPath = path.dirname(absoluteFilePath);
    const safeNewFileName = path.basename(newFileName); // Chỉ lấy phần tên, loại bỏ đường dẫn nếu có
    
    if (!safeNewFileName) {
      return res.status(400).json({ error: 'Tên file mới không hợp lệ' });
    }

    const absoluteNewFilePath = path.resolve(directoryPath, safeNewFileName);

    if (fs.existsSync(absoluteNewFilePath)) {
      return res.status(400).json({ error: 'Tên file mới đã tồn tại' });
    }

    fs.renameSync(absoluteFilePath, absoluteNewFilePath);

    return res.status(200).json({
      message: 'Đổi tên file thành công',
      data: {
        username,
        oldFilePath: filePath,
        newFileName: safeNewFileName
      }
    });

  } catch (error: any) {
    return res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
  }
};

// API đổi tên thư mục
export const renameFolder = (req: Request, res: Response) => {
  try {
    const { username, folderPath, newFolderName } = req.body;

    if (!username || !folderPath || !newFolderName) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username, folderPath, newFolderName)' });
    }

    const userRootPath = path.resolve(__dirname, '../../data', username);
    
    // Kiểm tra đường dẫn thư mục
    const absoluteFolderPath = path.resolve(userRootPath, folderPath);

    // Chống Path Traversal
    if (!absoluteFolderPath.startsWith(userRootPath)) {
      return res.status(403).json({ error: 'Đường dẫn thư mục không hợp lệ' });
    }

    // Không cho phép đổi tên thư mục gốc của user
    if (absoluteFolderPath === userRootPath) {
      return res.status(400).json({ error: 'Không thể đổi tên thư mục gốc của người dùng' });
    }

    if (!fs.existsSync(absoluteFolderPath)) {
      return res.status(404).json({ error: 'Thư mục không tồn tại' });
    }

    // Đảm bảo đây là thư mục
    if (!fs.statSync(absoluteFolderPath).isDirectory()) {
      return res.status(400).json({ error: 'Đường dẫn yêu cầu không trỏ tới một thư mục' });
    }

    // Lấy thư mục cha chứa thư mục hiện tại và tạo đường dẫn mới
    const directoryPath = path.dirname(absoluteFolderPath);
    const safeNewFolderName = path.basename(newFolderName); // Chỉ lấy phần tên, loại bỏ đường dẫn nếu có
    
    if (!safeNewFolderName) {
      return res.status(400).json({ error: 'Tên thư mục mới không hợp lệ' });
    }

    const absoluteNewFolderPath = path.resolve(directoryPath, safeNewFolderName);

    if (fs.existsSync(absoluteNewFolderPath)) {
      return res.status(400).json({ error: 'Tên thư mục mới đã tồn tại' });
    }

    fs.renameSync(absoluteFolderPath, absoluteNewFolderPath);

    return res.status(200).json({
      message: 'Đổi tên thư mục thành công',
      data: {
        username,
        oldFolderPath: folderPath,
        newFolderName: safeNewFolderName
      }
    });

  } catch (error: any) {
    return res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
  }
};

// API tìm kiếm file/thư mục theo tên
export const searchFiles = (req: Request, res: Response) => {
  try {
    const { username, keyword } = req.body;

    if (!username || !keyword) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username, keyword)' });
    }

    const userRootPath = path.resolve(__dirname, '../../data', username);
    
    if (!fs.existsSync(userRootPath)) {
      return res.status(404).json({ error: 'Thư mục người dùng không tồn tại' });
    }

    const results: any[] = [];
    const searchKeyword = keyword.toLowerCase();

    // Hàm đệ quy duyệt qua các file và thư mục để tìm kiếm
    const searchInDirectory = (dirPath: string) => {
      const items = fs.readdirSync(dirPath, { withFileTypes: true });
      for (const item of items) {
        const itemPath = path.join(dirPath, item.name);
        
        // Tạo đường dẫn tương đối so với thư mục gốc của user
        // (Thay thế dấu backslash của Windows bằng xuyệt chuẩn '/' cho dễ dùng trên frontend)
        const relativePath = path.relative(userRootPath, itemPath).replace(/\\/g, '/');

        // Kiểm tra xem tên file hoặc thư mục có chứa từ khóa hay không
        if (item.name.toLowerCase().includes(searchKeyword)) {
          try {
            const stats = fs.statSync(itemPath);
            results.push({
              name: item.name,
              path: relativePath,
              type: item.isDirectory() ? 'folder' : 'file',
              size: item.isFile() ? stats.size : undefined, // Trả về undefined với thư mục
              createdAt: stats.birthtime,
              modifiedAt: stats.mtime
            });
          } catch (err) {
            // Bỏ qua file lỗi, tiếp tục tìm kiếm
          }
        }

        // Đệ quy nếu là thư mục (Dù thư mục đó có chứa từ khoá hay không, ta vẫn vào tìm tiếp)
        if (item.isDirectory()) {
          searchInDirectory(itemPath);
        }
      }
    };

    searchInDirectory(userRootPath);

    return res.status(200).json({
      message: 'Tìm kiếm thành công',
      data: {
        username,
        keyword,
        results
      }
    });

  } catch (error: any) {
    return res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
  }
};