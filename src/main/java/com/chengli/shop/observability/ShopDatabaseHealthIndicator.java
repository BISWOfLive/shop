package com.chengli.shop.observability;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * readiness 的数据库结构检查：只查询 information_schema 中必需表的数量，不扫描业务表，
 * 也不在此处做全表统计。数据库为空或缺少基线表时返回 DOWN，避免空库被报告为业务就绪。
 */
@Component
public class ShopDatabaseHealthIndicator implements HealthIndicator {

    private static final List<String> REQUIRED_TABLES = List.of(
            "category", "product", "product_image",
            "inventory", "inventory_log",
            "shop_order", "order_item",
            "user_account", "role", "user_role",
            "outbox_event", "message_consume_record", "notification");

    private static final String SQL = "SELECT COUNT(DISTINCT table_name) FROM information_schema.tables "
            + "WHERE table_schema = DATABASE() AND table_name IN ("
            + String.join(",", REQUIRED_TABLES.stream().map(table -> "?").toList()) + ")";

    private final DataSource dataSource;

    public ShopDatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SQL)) {
            for (int index = 0; index < REQUIRED_TABLES.size(); index++) {
                statement.setString(index + 1, REQUIRED_TABLES.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                int found = result.next() ? result.getInt(1) : 0;
                if (found == REQUIRED_TABLES.size()) {
                    return Health.up().withDetail("requiredTables", REQUIRED_TABLES.size()).build();
                }
                // 详情不公开（show-details=never）；只用于内部监控定位空库或残缺基线。
                return Health.down()
                        .withDetail("foundRequiredTables", found)
                        .withDetail("requiredTables", REQUIRED_TABLES.size())
                        .build();
            }
        } catch (SQLException exception) {
            return Health.down().withDetail("errorType", exception.getClass().getSimpleName()).build();
        }
    }
}
