-- ============================================================
-- Migration: Thêm bảng 2FA cho thiết bị lạ
-- Chạy script này trên database ANDROID
-- ============================================================

-- Bảng thiết bị đã được tin tưởng
IF NOT EXISTS (SELECT 1 FROM sysobjects WHERE name='trusted_devices' AND xtype='U')
BEGIN
    CREATE TABLE trusted_devices (
        id           INT IDENTITY(1,1) PRIMARY KEY,
        user_id      INT NOT NULL,
        fingerprint  VARCHAR(64) NOT NULL,
        device_name  NVARCHAR(255) NULL,
        ip_address   VARCHAR(45) NULL,
        created_at   DATETIME NOT NULL DEFAULT GETDATE(),
        last_used_at DATETIME NOT NULL DEFAULT GETDATE(),

        CONSTRAINT FK_trusted_devices_user
            FOREIGN KEY (user_id) REFERENCES users(id)
            ON DELETE CASCADE,

        CONSTRAINT UQ_trusted_device UNIQUE (user_id, fingerprint)
    );

    CREATE INDEX IX_trusted_devices_user ON trusted_devices(user_id);

    PRINT 'Created table: trusted_devices';
END
ELSE
BEGIN
    PRINT 'Table trusted_devices already exists, skipped.';
END

-- Bảng OTP tạm thời chờ xác minh thiết bị mới
IF NOT EXISTS (SELECT 1 FROM sysobjects WHERE name='device_otp_pending' AND xtype='U')
BEGIN
    CREATE TABLE device_otp_pending (
        id             INT IDENTITY(1,1) PRIMARY KEY,
        user_id        INT NOT NULL,
        fingerprint    VARCHAR(64) NOT NULL,
        device_name    NVARCHAR(255) NULL,
        ip_address     VARCHAR(45) NULL,
        otp_code       VARCHAR(10) NOT NULL,
        otp_expires_at DATETIME NOT NULL,
        temp_token     VARCHAR(64) NOT NULL UNIQUE,
        created_at     DATETIME NOT NULL DEFAULT GETDATE(),

        CONSTRAINT FK_device_otp_user
            FOREIGN KEY (user_id) REFERENCES users(id)
            ON DELETE CASCADE
    );

    PRINT 'Created table: device_otp_pending';
END
ELSE
BEGIN
    PRINT 'Table device_otp_pending already exists, skipped.';
END

PRINT 'Migration 001_add_2fa_tables completed.';
