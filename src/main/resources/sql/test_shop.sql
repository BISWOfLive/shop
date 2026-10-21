-- 精简商城 test_shop：从空库开始的完整建库入口，2026-10-20。
-- 13张业务表；只保留逻辑关联，不创建物理外键，也不使用触发器代替关联校验。
-- 所有关联有效性、对象归属和跨表一致性由应用层及本地事务维护。
-- MySQL 8.0.16+，InnoDB，utf8mb4；人民币；单商家、单仓库、单规格商品。
-- 允许目标库已存在但为空；表不使用IF NOT EXISTS，不掩盖重复初始化或结构冲突。
-- 不包含删库、删表和旧库迁移；不由Spring Boot自动执行。

CREATE DATABASE IF NOT EXISTS test_shop
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE test_shop;
SET NAMES utf8mb4;
SET time_zone = '+08:00';

CREATE TABLE category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    name VARCHAR(64) NOT NULL COMMENT '分类名称',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序，越小越靠前',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类';

-- 商品资料与主图；默认下架，设置为上架时必须提供主图。
CREATE TABLE product (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID，当前同时作为单规格销售单元ID',
    name VARCHAR(120) NOT NULL COMMENT '商品名称',
    subtitle VARCHAR(200) NOT NULL DEFAULT '' COMMENT '可选的短卖点，不代替完整描述或结构化规格',
    category_id BIGINT NOT NULL COMMENT '所属分类',
    price DECIMAL(12,2) NOT NULL COMMENT '当前售价，单位元',
    description VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '商品描述',
    image_url VARCHAR(512) NOT NULL DEFAULT '' COMMENT '唯一主图的稳定地址；下架时可暂缺，上架必填',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0下架，1上架；新建默认下架',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_product_category_status_price (category_id, status, price ASC, id DESC),
    KEY idx_product_status_created (status, created_at DESC, id DESC),
    -- 全站低价查询不限定分类，使用status等值前缀后按price/id有序读取。
    KEY idx_product_status_price (status, price ASC, id DESC),
    -- 分类最近新增：先限定分类和状态，再按创建时间与ID降序读取。
    KEY idx_product_category_status_created (category_id, status, created_at DESC, id DESC),
    CONSTRAINT chk_product_price CHECK (price > 0),
    CONSTRAINT chk_product_status CHECK (status IN (0,1)),
    CONSTRAINT chk_product_publish_image CHECK (status = 0 OR CHAR_LENGTH(TRIM(image_url)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品';

-- 商品展示扩展：仅存轮播附图与详情图，主图仍以product.image_url为唯一来源。
-- 初期业务限制附图最多8张、详情图最多20张；不在数据库中用跨行CHECK计数。
CREATE TABLE product_image (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '图片记录ID',
    product_id BIGINT NOT NULL COMMENT '所属商品',
    image_url VARCHAR(512) NOT NULL COMMENT '稳定图片地址，不存图片二进制或短期签名URL',
    image_type TINYINT NOT NULL COMMENT '1轮播附图，不含主图；2图文详情图片',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '同类型内的顺序，越小越靠前，同值按ID排序',
    alt_text VARCHAR(120) NOT NULL DEFAULT '' COMMENT '图片文字说明，可为空；前端可回退商品名称',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_product_image_display (product_id, image_type, sort_order, id),
    CONSTRAINT chk_product_image_url CHECK (CHAR_LENGTH(TRIM(image_url)) > 0),
    CONSTRAINT chk_product_image_type CHECK (image_type IN (1,2)),
    CONSTRAINT chk_product_image_order CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品轮播附图与详情图片';

-- 用户及订单买家身份；禁用账号可不设口令，启用登录前必须写入编码结果。
-- 本脚本不创建默认登录账号，不写入明文或共享的初始口令。
CREATE TABLE user_account (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '登录名，不区分大小写',
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'PasswordEncoder编码结果，禁止存明文',
    nickname VARCHAR(64) NOT NULL DEFAULT '' COMMENT '展示昵称',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0禁用，1启用；默认禁用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_username (username),
    CONSTRAINT chk_user_account_status CHECK (status IN (0,1)),
    CONSTRAINT chk_user_account_password CHECK (
        status = 0 OR (password_hash IS NOT NULL AND CHAR_LENGTH(password_hash) > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账号';

-- 应用角色；脚本末尾初始化ADMIN与USER。
CREATE TABLE role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务角色码，例如ADMIN、USER',
    name VARCHAR(64) NOT NULL COMMENT '角色名称',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用角色';

CREATE TABLE user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_role_role (role_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

-- 每个商品至多一条库存；开发库存模块时，由商品创建事务同步初始化零库存。
CREATE TABLE inventory (
    product_id BIGINT NOT NULL COMMENT '商品ID，同时作为库存主键',
    available_qty INT NOT NULL DEFAULT 0 COMMENT '可售库存，未支付订单已扣除占用数量',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (product_id),
    CONSTRAINT chk_inventory_available CHECK (available_qty >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单仓单规格可售库存';

-- 订单状态、请求幂等、下单金额与支付到期时间。
CREATE TABLE shop_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库订单ID',
    order_no VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '对外订单号，服务端生成',
    buyer_id BIGINT NOT NULL COMMENT '买家ID，从服务端身份上下文取得',
    idempotency_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '一次下单意图的请求号，重试时复用',
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化商品ID和数量的SHA-256十六进制摘要',
    total_amount DECIMAL(18,2) NOT NULL COMMENT '订单总额，服务端按明细价格快照计算，单位元',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付，1已支付，2已取消',
    expires_at DATETIME(3) NOT NULL COMMENT '支付截止时间，初期为创建后30分钟',
    paid_at DATETIME(3) DEFAULT NULL COMMENT '模拟支付成功时间',
    cancelled_at DATETIME(3) DEFAULT NULL COMMENT '取消时间',
    cancel_reason VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'USER主动取消、TIMEOUT到期取消',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shop_order_no (order_no),
    UNIQUE KEY uk_shop_order_buyer_request (buyer_id, idempotency_key),
    KEY idx_shop_order_buyer_created (buyer_id, created_at DESC, id DESC),
    KEY idx_shop_order_status_expires (status, expires_at, id),
    CONSTRAINT chk_shop_order_amount CHECK (total_amount > 0),
    CONSTRAINT chk_shop_order_status CHECK (status IN (0,1,2)),
    CONSTRAINT chk_shop_order_expiry CHECK (expires_at > created_at),
    CONSTRAINT chk_shop_order_reason CHECK (cancel_reason IS NULL OR cancel_reason IN ('USER','TIMEOUT')),
    CONSTRAINT chk_shop_order_state_fields CHECK (
        (status = 0 AND paid_at IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL)
        OR (status = 1 AND paid_at IS NOT NULL AND cancelled_at IS NULL AND cancel_reason IS NULL)
        OR (status = 2 AND paid_at IS NULL AND cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

CREATE TABLE order_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    order_id BIGINT NOT NULL COMMENT '所属订单',
    product_id BIGINT NOT NULL COMMENT '下单商品ID',
    product_name VARCHAR(120) NOT NULL COMMENT '下单时商品名称快照',
    product_image_url VARCHAR(512) NOT NULL DEFAULT '' COMMENT '下单时主图地址快照，不保证外部图片永久有效',
    unit_price DECIMAL(12,2) NOT NULL COMMENT '下单时单价快照，单位元',
    quantity INT NOT NULL COMMENT '购买数量，1至9999',
    line_amount DECIMAL(18,2) GENERATED ALWAYS AS (unit_price * quantity) STORED COMMENT '明细金额，由数据库生成',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_item_order_product (order_id, product_id),
    KEY idx_order_item_product (product_id),
    CONSTRAINT chk_order_item_price CHECK (unit_price > 0),
    CONSTRAINT chk_order_item_quantity CHECK (quantity BETWEEN 1 AND 9999)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细及商品快照';

CREATE TABLE inventory_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '流水ID',
    product_id BIGINT NOT NULL COMMENT '库存商品ID',
    order_id BIGINT DEFAULT NULL COMMENT '扣减或归还关联订单，入库和调整时为空',
    actor_id BIGINT DEFAULT NULL COMMENT '管理员操作人；系统库存操作可为空',
    change_type TINYINT NOT NULL COMMENT '1入库，2下单扣减，3取消归还，4人工调整',
    biz_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务幂等号；订单操作使用order_no，入库调整使用操作请求号',
    before_qty INT NOT NULL COMMENT '变更前可售库存',
    change_qty INT NOT NULL COMMENT '有符号变更量：扣减为负，增加为正',
    after_qty INT NOT NULL COMMENT '变更后可售库存',
    remark VARCHAR(255) NOT NULL DEFAULT '' COMMENT '变更原因说明',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_log_business (change_type, biz_no, product_id),
    KEY idx_inventory_log_product_created (product_id, created_at DESC, id DESC),
    KEY idx_inventory_log_order (order_id),
    KEY idx_inventory_log_actor_created (actor_id, created_at DESC, id DESC),
    CONSTRAINT chk_inventory_log_type CHECK (change_type IN (1,2,3,4)),
    CONSTRAINT chk_inventory_log_balance CHECK (
        before_qty >= 0 AND after_qty >= 0 AND change_qty <> 0 AND after_qty = before_qty + change_qty
    ),
    CONSTRAINT chk_inventory_log_direction CHECK (
        (change_type IN (1,3) AND change_qty > 0)
        OR (change_type = 2 AND change_qty < 0)
        OR (change_type = 4 AND change_qty <> 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='只追加的库存变更流水';

-- 业务数据和事件同一事务提交；消息组件接入后由后台投递RabbitMQ。
CREATE TABLE outbox_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '投递任务ID',
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全局事件UUID，重投不得更换',
    aggregate_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合类型，初期为ORDER',
    aggregate_id BIGINT NOT NULL COMMENT '聚合ID，仅作逻辑关联',
    event_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ORDER_CREATED、ORDER_PAID、ORDER_CANCELLED等',
    payload_version INT NOT NULL DEFAULT 1 COMMENT '事件契约版本',
    payload JSON NOT NULL COMMENT '事件数据：买家ID、订单号、到期时间等必要快照',
    publish_status TINYINT NOT NULL DEFAULT 0 COMMENT '0待发送，1发送中，2已发送，3停止自动重试',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '失败重试计数',
    available_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次允许发送的时间，不是订单到期时间',
    locked_by VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '本次任务领取的唯一令牌',
    locked_until DATETIME(3) DEFAULT NULL COMMENT '任务领取租约截止时间',
    last_error VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '最近一次投递失败摘要',
    published_at DATETIME(3) DEFAULT NULL COMMENT '收到发布确认且路由成功后的时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_due (publish_status, available_at, id),
    KEY idx_outbox_lease (publish_status, locked_until, id),
    CONSTRAINT chk_outbox_status CHECK (publish_status IN (0,1,2,3)),
    CONSTRAINT chk_outbox_retry CHECK (retry_count >= 0),
    CONSTRAINT chk_outbox_payload_version CHECK (payload_version >= 1),
    CONSTRAINT chk_outbox_lease CHECK (
        (publish_status = 1 AND locked_by IS NOT NULL AND locked_until IS NOT NULL)
        OR (publish_status IN (0,2,3) AND locked_by IS NULL AND locked_until IS NULL)
    ),
    CONSTRAINT chk_outbox_published CHECK (
        (publish_status = 2 AND published_at IS NOT NULL)
        OR (publish_status IN (0,1,3) AND published_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务事件及可靠投递任务';

-- 消费去重记录与消费方业务表同库、同事务提交。
-- 未来通知服务拆分时，在通知库建立同结构的消费记录表。
CREATE TABLE message_consume_record (
    consumer_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定的逻辑消费方名称，不是进程实例名',
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务事件UUID，不是RabbitMQ deliveryTag',
    event_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件类型',
    processed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '成功事务内写入的处理时间',
    PRIMARY KEY (consumer_name, event_id),
    KEY idx_message_consume_processed (processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已成功处理的消息幂等记录';

-- 站内通知；未来拆服务时由通知服务拥有通知表。
-- 接收人、订单号和事件ID都只作逻辑关联。
CREATE TABLE notification (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源事件UUID',
    recipient_user_id BIGINT NOT NULL COMMENT '接收用户ID，逻辑引用用户服务/商城账号',
    order_no VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '关联订单号快照，用于页面跳转',
    title VARCHAR(120) NOT NULL COMMENT '通知标题快照',
    content VARCHAR(2000) NOT NULL COMMENT '通知正文快照',
    read_at DATETIME(3) DEFAULT NULL COMMENT '未读时为空，首次阅读后记录时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_event_recipient (event_id, recipient_user_id),
    KEY idx_notification_recipient_created (recipient_user_id, created_at DESC, id DESC),
    KEY idx_notification_recipient_read (recipient_user_id, read_at, id),
    CONSTRAINT chk_notification_read_time CHECK (read_at IS NULL OR read_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';

-- 基础字典：仅初始化分类与角色，不造商品、库存、订单或登录账号。
INSERT INTO category (name, sort_order) VALUES
    ('数码配件', 10),
    ('家居日用', 20),
    ('办公文具', 30),
    ('服饰箱包', 40);

INSERT INTO role (code, name) VALUES
    ('ADMIN', '管理员'),
    ('USER', '普通用户');

