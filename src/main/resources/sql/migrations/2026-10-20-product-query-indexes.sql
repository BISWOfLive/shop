-- 已有test_shop库的增量索引；不重建库或表，不修改商品数据。
-- 执行前读取SHOW INDEX FROM product，确认同名或同结构索引尚不存在。
-- 已执行这两条语句的数据库无需再次执行；本脚本是一次性迁移，并非幂等脚本。
USE test_shop;

-- WHERE status=1 ORDER BY price ASC,id DESC LIMIT ...
-- 原(category_id,status,price ASC,id DESC)按分类分组，不能直接提供全站价格顺序。
CREATE INDEX idx_product_status_price
ON product (status, price ASC, id DESC);

-- WHERE category_id=? AND status=1 ORDER BY created_at DESC,id DESC LIMIT ...
-- 等值过滤放在前面，剩余字段与业务排序及同值ID边界保持一致。
CREATE INDEX idx_product_category_status_created
ON product (category_id, status, created_at DESC, id DESC);
