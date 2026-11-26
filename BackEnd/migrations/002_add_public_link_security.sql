-- ============================================================
-- Migration: Thêm bảo mật mật khẩu (Bcrypt) & One-time link cho public_links
-- Chạy script này trên database ANDROID
-- ============================================================

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('public_links') AND name = 'password_hash'
)
BEGIN
    ALTER TABLE public_links ADD password_hash VARCHAR(255) NULL;
    PRINT 'Added column: public_links.password_hash';
END

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('public_links') AND name = 'is_one_time'
)
BEGIN
    ALTER TABLE public_links ADD is_one_time BIT NOT NULL DEFAULT 0;
    PRINT 'Added column: public_links.is_one_time';
END

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('public_links') AND name = 'download_count'
)
BEGIN
    ALTER TABLE public_links ADD download_count INT NOT NULL DEFAULT 0;
    PRINT 'Added column: public_links.download_count';
END

PRINT 'Migration 002_add_public_link_security completed.';
