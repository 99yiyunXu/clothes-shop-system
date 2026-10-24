-- 実行前に既存データベースをバックアップする
-- このファイルはアプリケーションから自動実行しない
-- 対象は既存の服装店データベースとする
USE luna_shop;

CREATE TABLE IF NOT EXISTS products (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255), price INT, image_url VARCHAR(255), category VARCHAR(255),
    description VARCHAR(2000) DEFAULT '', stock INT NOT NULL DEFAULT 0,
    active BIT(1) NOT NULL DEFAULT b'1', sale BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 既存商品の番号、名前、価格、画像と分類は変更しない
-- 在庫は確認するまでゼロとし、登録日時の不明な商品には移行日時を設定する
DELIMITER $$
DROP PROCEDURE IF EXISTS luna_shop_upgrade_v1$$
CREATE PROCEDURE luna_shop_upgrade_v1()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'products' AND column_name = 'description') THEN
        ALTER TABLE products ADD COLUMN description VARCHAR(2000) DEFAULT '';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'products' AND column_name = 'stock') THEN
        ALTER TABLE products ADD COLUMN stock INT NOT NULL DEFAULT 0;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'products' AND column_name = 'active') THEN
        ALTER TABLE products ADD COLUMN active BIT(1) NOT NULL DEFAULT b'1';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'products' AND column_name = 'sale') THEN
        ALTER TABLE products ADD COLUMN sale BIT(1) NOT NULL DEFAULT b'0';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'products' AND column_name = 'created_at') THEN
        ALTER TABLE products ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'products' AND index_name = 'idx_products_active_created') THEN
        CREATE INDEX idx_products_active_created ON products(active, created_at, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'products' AND column_name = 'version') THEN
        ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
    END IF;
END$$
DELIMITER ;
CALL luna_shop_upgrade_v1();
DROP PROCEDURE luna_shop_upgrade_v1;

CREATE TABLE IF NOT EXISTS shop_users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_user_role CHECK (role IN ('USER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cart_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL, product_id INT NOT NULL, quantity INT NOT NULL,
    UNIQUE KEY uk_cart_user_product (user_id, product_id),
    FOREIGN KEY (user_id) REFERENCES shop_users(id),
    FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT chk_cart_quantity CHECK (quantity BETWEEN 1 AND 99)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS favorites (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL, product_id INT NOT NULL,
    UNIQUE KEY uk_favorite_user_product (user_id, product_id),
    FOREIGN KEY (user_id) REFERENCES shop_users(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shop_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL, checkout_token VARCHAR(36) NOT NULL,
    status VARCHAR(30) NOT NULL, subtotal BIGINT NOT NULL, shipping_fee BIGINT NOT NULL, total BIGINT NOT NULL,
    recipient VARCHAR(100) NOT NULL, address VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL, paid_at DATETIME(6),
    tracking_number VARCHAR(100), return_reason VARCHAR(500),
    UNIQUE KEY uk_order_checkout (user_id, checkout_token),
    INDEX idx_orders_user_created (user_id, id),
    FOREIGN KEY (user_id) REFERENCES shop_users(id),
    CONSTRAINT chk_order_total CHECK (subtotal >= 0 AND shipping_fee >= 0 AND total = subtotal + shipping_fee)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL, product_id INT NOT NULL,
    product_name VARCHAR(255) NOT NULL, image_url VARCHAR(255),
    unit_price INT NOT NULL, quantity INT NOT NULL,
    FOREIGN KEY (order_id) REFERENCES shop_orders(id),
    CONSTRAINT chk_order_item CHECK (unit_price >= 0 AND quantity BETWEEN 1 AND 99)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
