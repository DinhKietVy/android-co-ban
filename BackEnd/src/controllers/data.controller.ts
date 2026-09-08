import { Request, Response } from 'express';
import fs from 'fs';
import path from 'path';
import multer from 'multer';
import sharp from 'sharp';
import ffmpeg from 'fluent-ffmpeg';
import libre from 'libreoffice-convert';
import util from 'util';

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

    let absoluteTargetFolderPath = path.resolve(userRootPath, targetFolderPath);
    let fileName = path.basename(absoluteSourcePath);
    let newFilePath = '';
    let metaFilePath = '';

    if (targetFolderPath === "RESTORE" && sourceFilePath.startsWith("trash")) {
      // Khôi phục từ thùng rác
      metaFilePath = absoluteSourcePath + '.meta.json';
      if (fs.existsSync(metaFilePath)) {
        const meta = JSON.parse(fs.readFileSync(metaFilePath, 'utf8'));
        const originalPathMeta = meta.originalPath;
        absoluteTargetFolderPath = path.resolve(userRootPath, path.dirname(originalPathMeta));
        fileName = path.basename(originalPathMeta);
      } else {
        // Fallback về thư mục gốc nếu không có metadata
        absoluteTargetFolderPath = userRootPath;
        fileName = fileName.replace(/^\d{13}_/, '');
      }

      if (!fs.existsSync(absoluteTargetFolderPath)) {
        fs.mkdirSync(absoluteTargetFolderPath, { recursive: true });
      }
      newFilePath = path.join(absoluteTargetFolderPath, fileName);

      // Chống trùng lặp khi khôi phục
      let counter = 1;
      const ext = path.extname(fileName);
      const base = path.basename(fileName, ext);
      while (fs.existsSync(newFilePath)) {
        newFilePath = path.join(absoluteTargetFolderPath, `${base} (${counter})${ext}`);
        counter++;
      }
    } else {
      // Di chuyển thông thường hoặc vào trash
      if (!absoluteTargetFolderPath.startsWith(userRootPath)) {
        return res.status(403).json({ error: 'Đường dẫn thư mục đích không hợp lệ' });
      }

      if (!fs.existsSync(absoluteTargetFolderPath) || !fs.statSync(absoluteTargetFolderPath).isDirectory()) {
        return res.status(404).json({ error: 'Thư mục đích không tồn tại' });
      }

      if (targetFolderPath === "trash") {
        fileName = `${Date.now()}_${fileName}`;
      }
      newFilePath = path.join(absoluteTargetFolderPath, fileName);

      if (fs.existsSync(newFilePath)) {
        return res.status(400).json({ error: 'Đã có file cùng tên tại thư mục đích' });
      }
    }

    fs.renameSync(absoluteSourcePath, newFilePath);

    // Xử lý metadata
    if (targetFolderPath === "trash") {
      fs.writeFileSync(newFilePath + '.meta.json', JSON.stringify({ originalPath: sourceFilePath, deletedAt: Date.now() }));
    } else if (targetFolderPath === "RESTORE" && metaFilePath && fs.existsSync(metaFilePath)) {
      fs.unlinkSync(metaFilePath);
    }

    return res.status(200).json({
      message: targetFolderPath === "RESTORE" ? 'Khôi phục file thành công' : 'Di chuyển file thành công',
      data: {
        username,
        fileName: path.basename(newFilePath),
        from: sourceFilePath,
        to: targetFolderPath
      }
    });

  } catch (error: any) {
    return res.status(500).json({ error: 'Lỗi máy chủ', detail: error.message });
  }
};

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

    let absoluteTargetFolderPath = path.resolve(userRootPath, targetFolderPath);
    let folderName = path.basename(absoluteSourcePath);
    let newFolderPath = '';
    let metaFilePath = '';

    if (targetFolderPath === "RESTORE" && sourceFolderPath.startsWith("trash")) {
      // Khôi phục từ thùng rác
      metaFilePath = absoluteSourcePath + '.meta.json';
      if (fs.existsSync(metaFilePath)) {
        const meta = JSON.parse(fs.readFileSync(metaFilePath, 'utf8'));
        const originalPathMeta = meta.originalPath;
        absoluteTargetFolderPath = path.resolve(userRootPath, path.dirname(originalPathMeta));
        folderName = path.basename(originalPathMeta);
      } else {
        absoluteTargetFolderPath = userRootPath;
        folderName = folderName.replace(/^\d{13}_/, '');
      }

      if (!fs.existsSync(absoluteTargetFolderPath)) {
        fs.mkdirSync(absoluteTargetFolderPath, { recursive: true });
      }
      newFolderPath = path.join(absoluteTargetFolderPath, folderName);

      // Chống trùng lặp khi khôi phục
      let counter = 1;
      const base = folderName;
      while (fs.existsSync(newFolderPath)) {
        newFolderPath = path.join(absoluteTargetFolderPath, `${base} (${counter})`);
        counter++;
      }
    } else {
      // Kiểm tra đường dẫn thư mục đích
      if (!absoluteTargetFolderPath.startsWith(userRootPath)) {
        return res.status(403).json({ error: 'Đường dẫn thư mục đích không hợp lệ' });
      }

      if (!fs.existsSync(absoluteTargetFolderPath) || !fs.statSync(absoluteTargetFolderPath).isDirectory()) {
        return res.status(404).json({ error: 'Thư mục đích không tồn tại' });
      }

      // Đảm bảo không di chuyển thư mục cha vào trong thư mục con
      if (absoluteTargetFolderPath === absoluteSourcePath || absoluteTargetFolderPath.startsWith(absoluteSourcePath + path.sep)) {
        return res.status(400).json({ error: 'Không thể di chuyển thư mục vào bên trong chính nó hoặc các thư mục con của nó' });
      }

      if (targetFolderPath === "trash") {
        folderName = `${Date.now()}_${folderName}`;
      }
      newFolderPath = path.join(absoluteTargetFolderPath, folderName);

      if (fs.existsSync(newFolderPath)) {
        return res.status(400).json({ error: 'Đã có thư mục hoặc file cùng tên tại vị trí đích' });
      }
    }

    // Di chuyển thư mục
    fs.renameSync(absoluteSourcePath, newFolderPath);

    // Xử lý metadata
    if (targetFolderPath === "trash") {
      fs.writeFileSync(newFolderPath + '.meta.json', JSON.stringify({ originalPath: sourceFolderPath, deletedAt: Date.now() }));
    } else if (targetFolderPath === "RESTORE" && metaFilePath && fs.existsSync(metaFilePath)) {
      fs.unlinkSync(metaFilePath);
    }

    return res.status(200).json({
      message: targetFolderPath === "RESTORE" ? 'Khôi phục thư mục thành công' : 'Di chuyển thư mục thành công',
      data: {
        username,
        folderName: path.basename(newFolderPath),
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

    // Hàm đệ quy tính tổng dung lượng của toàn bộ thư mục user
    const getTotalSize = (dirPath: string): number => {
      let totalSize = 0;
      try {
        const dirItems = fs.readdirSync(dirPath, { withFileTypes: true });
        for (const dirItem of dirItems) {
          const childPath = path.join(dirPath, dirItem.name);
          if (dirItem.isDirectory()) {
            const lowerName = dirItem.name.toLowerCase();
            if (lowerName !== 'ai' && lowerName !== '.ai') {
              totalSize += getTotalSize(childPath);
            }
          } else if (dirItem.isFile()) {
            try {
              totalSize += fs.statSync(childPath).size;
            } catch (e) {}
          }
        }
      } catch (e) {}
      return totalSize;
    };

    const totalUsedBytes = getTotalSize(userRootPath);

    // Đọc danh sách các mục bên trong thư mục
    const items = fs.readdirSync(absoluteFolderPath, { withFileTypes: true });

    const folders: any[] = [];
    const files: any[] = [];

    for (const item of items) {
      if (item.name.endsWith('.meta.json')) {
        continue;
      }
      
      const itemPath = path.join(absoluteFolderPath, item.name);
      try {
        const stats = fs.statSync(itemPath);
        let itemModifiedAt = stats.mtime;

        if (folderPath === 'trash') {
          const metaPath = itemPath + '.meta.json';
          if (fs.existsSync(metaPath)) {
            try {
              const meta = JSON.parse(fs.readFileSync(metaPath, 'utf8'));
              if (meta.deletedAt) {
                itemModifiedAt = new Date(meta.deletedAt);
              }
            } catch(e) {}
          }
        }

        if (item.isDirectory()) {
          // Tính số lượng mục con ngay bên trong thư mục này
          let itemCount = 0;
          try {
            // Lọc bỏ file .meta.json khi đếm số lượng mục con
            const childItems = fs.readdirSync(itemPath);
            itemCount = childItems.filter(child => !child.endsWith('.meta.json')).length;
          } catch (e) {
            // Bỏ qua nếu không có quyền đọc
          }

          folders.push({
            name: item.name,
            itemCount: itemCount,
            createdAt: stats.birthtime,
            modifiedAt: itemModifiedAt
          });
        } else if (item.isFile()) {
          files.push({
            name: item.name,
            size: stats.size, // Kích thước tính bằng byte
            createdAt: stats.birthtime,
            modifiedAt: itemModifiedAt
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
        totalUsedBytes,
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

// =====================================================================
// API chuyển đổi định dạng file (Universal Format Converter)
// =====================================================================

const libreConvertAsync = util.promisify(libre.convert);

// Nếu soffice không nằm trong PATH, có thể set biến môi trường SOFFICE_PATH trong .env
// Ví dụ: SOFFICE_PATH=C:\Program Files\LibreOffice\program\soffice.exe
if (process.env.SOFFICE_PATH) {
  (libre as any).soffice = process.env.SOFFICE_PATH;
}

// Danh sách định dạng được phép (whitelist) để tránh rủi ro bảo mại
const ALLOWED_IMAGE_FORMATS = ['jpg', 'jpeg', 'png', 'webp', 'bmp', 'tiff', 'gif', 'avif'];
const ALLOWED_VIDEO_FORMATS = ['mp4', 'mkv', 'avi', 'mov', 'wmv', 'flv', 'webm', 'm4v'];
const ALLOWED_AUDIO_FORMATS = ['mp3', 'wav', 'm4a', 'ogg', 'aac', 'flac', 'wma'];
const ALLOWED_DOCUMENT_FORMATS = ['pdf']; // LibreOffice chỉ hỗ trợ xuất sang PDF

const IMAGE_SOURCE_FORMATS = new Set(['jpg', 'jpeg', 'png', 'webp', 'bmp', 'tiff', 'gif', 'avif']);
const VIDEO_SOURCE_FORMATS = new Set(['mp4', 'mkv', 'avi', 'mov', 'wmv', 'flv', 'webm', 'm4v']);
const AUDIO_SOURCE_FORMATS = new Set(['mp3', 'wav', 'm4a', 'ogg', 'aac', 'flac', 'wma']);
const DOCUMENT_SOURCE_FORMATS = new Set(['docx', 'doc', 'xlsx', 'xls', 'pptx', 'ppt', 'odt', 'ods', 'odp']);

/**
 * Tạo đường dẫn output không bị trùng, tự động thêm hậu tố (1), (2)...
 */
const generateUniqueOutputPath = (dir: string, baseName: string, ext: string): string => {
  let outputPath = path.join(dir, `${baseName}.${ext}`);
  let counter = 1;
  while (fs.existsSync(outputPath)) {
    outputPath = path.join(dir, `${baseName} (${counter}).${ext}`);
    counter++;
  }
  return outputPath;
};

export const convertFile = async (req: Request, res: Response) => {
  try {
    const { username, filePath, targetFormat } = req.body;

    // --- Validate đầu vào ---
    if (!username || !filePath || !targetFormat) {
      return res.status(400).json({ error: 'Thiếu thông tin bắt buộc (username, filePath, targetFormat)' });
    }

    // Làm sạch targetFormat: chỉ cho phép chữ và số, loại bỏ dấu chấm đầu tiên nếu có
    const cleanTargetFormat = (targetFormat as string).toLowerCase().replace(/^\./, '').trim();

    // Validate targetFormat chỉ là chữ/số, không chứa ký tự nguy hiểm
    if (!/^[a-z0-9]+$/.test(cleanTargetFormat)) {
      return res.status(400).json({ error: 'Định dạng đích không hợp lệ' });
    }

    const userRootPath = path.resolve(__dirname, '../../data', username);
    const absoluteInputPath = path.resolve(userRootPath, filePath as string);

    // Chống Path Traversal
    if (!absoluteInputPath.startsWith(userRootPath)) {
      return res.status(403).json({ error: 'Đường dẫn file không hợp lệ' });
    }

    if (!fs.existsSync(absoluteInputPath)) {
      return res.status(404).json({ error: 'File nguồn không tồn tại' });
    }

    if (!fs.statSync(absoluteInputPath).isFile()) {
      return res.status(400).json({ error: 'Đường dẫn yêu cầu không trỏ tới một file' });
    }

    // Lấy thông tin file nguồn
    const sourceExt = path.extname(absoluteInputPath).toLowerCase().replace('.', '');
    const sourceBaseName = path.basename(absoluteInputPath, path.extname(absoluteInputPath));
    const sourceDir = path.dirname(absoluteInputPath);

    // Tạo đường dẫn output không trùng lặp
    const outputPath = generateUniqueOutputPath(sourceDir, sourceBaseName, cleanTargetFormat);
    const relativeNewPath = path.relative(userRootPath, outputPath).replace(/\\/g, '/');
    const relativeOriginalPath = path.relative(userRootPath, absoluteInputPath).replace(/\\/g, '/');

    // --- Phân loại và gọi engine tương ứng ---

    if (IMAGE_SOURCE_FORMATS.has(sourceExt)) {
      // --- Engine Ảnh (Sharp) ---
      if (!ALLOWED_IMAGE_FORMATS.includes(cleanTargetFormat)) {
        return res.status(400).json({ error: `Không hỗ trợ chuyển đổi ảnh sang định dạng .${cleanTargetFormat}. Hỗ trợ: ${ALLOWED_IMAGE_FORMATS.join(', ')}` });
      }

      await sharp(absoluteInputPath)
        .toFormat(cleanTargetFormat as keyof sharp.FormatEnum)
        .toFile(outputPath);

    } else if (AUDIO_SOURCE_FORMATS.has(sourceExt) || VIDEO_SOURCE_FORMATS.has(sourceExt)) {
      // --- Engine Audio/Video (FFmpeg) ---
      const allowedMedia = [...ALLOWED_VIDEO_FORMATS, ...ALLOWED_AUDIO_FORMATS];
      if (!allowedMedia.includes(cleanTargetFormat)) {
        return res.status(400).json({ error: `Không hỗ trợ chuyển đổi media sang định dạng .${cleanTargetFormat}. Hỗ trợ: ${allowedMedia.join(', ')}` });
      }

      await new Promise<void>((resolve, reject) => {
        ffmpeg(absoluteInputPath)
          .toFormat(cleanTargetFormat)
          .on('end', () => resolve())
          .on('error', (err: Error) => reject(err))
          .save(outputPath);
      });

    } else if (DOCUMENT_SOURCE_FORMATS.has(sourceExt)) {
      // --- Engine Tài liệu (LibreOffice) ---
      if (!ALLOWED_DOCUMENT_FORMATS.includes(cleanTargetFormat)) {
        return res.status(400).json({ error: `Không hỗ trợ chuyển đổi tài liệu sang định dạng .${cleanTargetFormat}. Hiện chỉ hỗ trợ: pdf` });
      }

      const fileData = fs.readFileSync(absoluteInputPath);
      const convertedBuffer = await libreConvertAsync(fileData, '.pdf', undefined);
      fs.writeFileSync(outputPath, convertedBuffer);

    } else {
      return res.status(400).json({
        error: `Định dạng nguồn .${sourceExt} không được hỗ trợ`,
        supportedImageFormats: ALLOWED_IMAGE_FORMATS,
        supportedVideoFormats: ALLOWED_VIDEO_FORMATS,
        supportedAudioFormats: ALLOWED_AUDIO_FORMATS,
        supportedDocumentFormats: [...DOCUMENT_SOURCE_FORMATS]
      });
    }

    return res.status(200).json({
      message: 'Chuyển đổi thành công',
      data: {
        originalPath: relativeOriginalPath,
        newPath: relativeNewPath
      }
    });

  } catch (error: any) {
    // Dọn dẹp file output nếu quá trình convert thất bại giữa chừng
    return res.status(500).json({ error: 'Lỗi trong quá trình chuyển đổi', detail: error.message });
  }
};
