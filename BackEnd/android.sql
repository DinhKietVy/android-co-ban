CREATE DATABASE ANDROID

CREATE TABLE users (
    id INT IDENTITY(1,1) PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name NVARCHAR(255) NOT NULL,
    reset_code VARCHAR(10) NULL,
    reset_code_expires_at DATETIME NULL
);

CREATE TABLE share_file (
    owner_id INT NOT NULL,
    target_id INT NOT NULL,
    file_path VARCHAR(255) NOT NULL,

    PRIMARY KEY (owner_id, target_id, file_path),

    CONSTRAINT FK_share_file_owner
        FOREIGN KEY (owner_id)
        REFERENCES users(id),

    CONSTRAINT FK_share_file_target
        FOREIGN KEY (target_id)
        REFERENCES users(id)
);

CREATE LOGIN myuser WITH PASSWORD = '123456';
CREATE USER myuser FOR LOGIN myuser;
ALTER ROLE db_owner ADD MEMBER myuser;

CREATE TABLE public_links (
    token VARCHAR(36) PRIMARY KEY,
    owner_id INT NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    created_at DATETIME DEFAULT GETDATE(),
    expires_at DATETIME NULL,
    CONSTRAINT FK_public_link_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

DELETE users

SELECT * FROM share_file
SELECT * FROM users


-- ============================================================
-- AI CHAT STORAGE
-- Bảng 1: chat_sessions - Mỗi cuộc hội thoại của user
-- Bảng 2: chat_messages  - Từng message trong cuộc hội thoại
-- ============================================================

-- Một user có thể có nhiều session (cuộc hội thoại) khác nhau
CREATE TABLE chat_sessions (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id     INT NOT NULL,
    -- Tên cuộc hội thoại, mặc định lấy từ prompt đầu tiên (cắt ngắn)
    title       NVARCHAR(255) NULL,
    -- Dịch vụ AI đang dùng: 'openai' | 'gemini'
    ai_service  VARCHAR(20) NOT NULL DEFAULT 'openai',
    created_at  DATETIME NOT NULL DEFAULT GETDATE(),
    updated_at  DATETIME NOT NULL DEFAULT GETDATE(),

    CONSTRAINT FK_chat_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
);

-- Mỗi message thuộc về một session, lưu từng turn của history
CREATE TABLE chat_messages (
    id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    session_id   BIGINT NOT NULL,
    -- 'user' hoặc 'model' (khớp với role trong history của openai_service.py)
    role         VARCHAR(10) NOT NULL,
    content      NVARCHAR(MAX) NOT NULL,
    -- File đính kèm trong turn này (nếu có), lưu relative path
    file_path    VARCHAR(500) NULL,
    -- Tool actions AI đã thực thi (JSON array), ví dụ: ["create_folder","move_files"]
    actions_executed NVARCHAR(MAX) NULL,
    -- Các item bị ảnh hưởng bởi action (JSON array)
    affected_items   NVARCHAR(MAX) NULL,
    created_at   DATETIME NOT NULL DEFAULT GETDATE(),

    CONSTRAINT FK_chat_messages_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions(id)
        ON DELETE CASCADE
);

-- Index để query nhanh history theo session
CREATE INDEX IX_chat_messages_session_id ON chat_messages(session_id);
-- Index để lấy danh sách session của user, sắp xếp mới nhất trước
CREATE INDEX IX_chat_sessions_user_updated ON chat_sessions(user_id, updated_at DESC);
