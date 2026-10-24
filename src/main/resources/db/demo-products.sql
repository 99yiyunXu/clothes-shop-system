-- 開発用の商品を必要な場合だけ手動で追加する
-- 同じ画像パスの商品が存在する場合は追加しない
USE luna_shop;
INSERT INTO products(name, price, image_url, category, description, stock, active, sale, created_at)
SELECT 'やわらかニット', 4900, '/images/products/knit1.png', 'レディース', '日常に合わせやすいニットです。', 10, b'1', b'0', CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/products/knit1.png');
INSERT INTO products(name, price, image_url, category, description, stock, active, sale, created_at)
SELECT 'シンプルコート', 12800, '/images/products/coat1.png', 'メンズ', '季節の変わり目に使えるコートです。', 5, b'1', b'1', CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/products/coat1.png');
