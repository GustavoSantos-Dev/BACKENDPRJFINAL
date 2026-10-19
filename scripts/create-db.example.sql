-- Execute no MySQL Workbench com seu administrador.
-- Substitua a senha de exemplo pela mesma senha definida em DB_PASSWORD no .env.
CREATE DATABASE IF NOT EXISTS production CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
CREATE USER IF NOT EXISTS 'production'@'localhost' IDENTIFIED BY 'SUBSTITUA_POR_SUA_SENHA';
GRANT ALL PRIVILEGES ON production.* TO 'production'@'localhost';