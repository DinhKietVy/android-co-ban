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

DELETE users