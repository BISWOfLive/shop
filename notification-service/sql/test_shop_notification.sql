-- 站内通知独立库 test_shop_notification 基线，阶段 9 新增，2026-10-29。
-- 只创建通知服务拥有的两张表；不复制 test_shop 其他表，不建物理外键。
-- 目标库可以不存在，或已存在但为空；表不使用 IF NOT EXISTS，重复导入会明确报错。
-- 本脚本不是增量迁移入口，也不包含删库、删表和历史通知回填。
--
-- 数据库账号权限说明（部署时单独执行，不在本脚本内创建账号）：
--   通知服务账号：只授予 test_shop_notification 上的 SELECT、INSERT、UPDATE；
--   不授予 DROP、ALTER、DELETE、CREATE、FILE、PROCESS 等权限；
--   不授予 test_shop（商城库）的任何权限，商城也不获得本库权限。

CREATE DATABASE IF NOT EXISTS test_shop_notification
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE test_shop_notification;
SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- 已成功处理的事件记录：与 notification 同库同事务写入，主键为完整复合键。
CREATE TABLE message_consume_record (
    consumer_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定的逻辑消费方名称，不是进程实例名',
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务事件UUID，不是RabbitMQ deliveryTag',
    event_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件类型',
    processed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '成功事务内写入的处理时间',
    PRIMARY KEY (consumer_name, event_id),
    KEY idx_message_consume_processed (processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知服务已成功处理的消息幂等记录';

-- 本人通知：接收人、订单号和事件ID都只作逻辑关联，不跨库 JOIN、不回查订单。
CREATE TABLE notification (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源事件UUID',
    recipient_user_id BIGINT NOT NULL COMMENT '接收用户ID，逻辑引用商城账号',
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
