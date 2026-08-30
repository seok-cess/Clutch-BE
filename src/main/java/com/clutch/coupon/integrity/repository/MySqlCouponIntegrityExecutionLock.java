package com.clutch.coupon.integrity.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
public class MySqlCouponIntegrityExecutionLock {
    private static final String ACQUIRE_SQL = "SELECT GET_LOCK(?, 0)";
    private static final String RELEASE_SQL = "SELECT RELEASE_LOCK(?)";

    private final JdbcTemplate jdbcTemplate;
    private final String lockName;

    public MySqlCouponIntegrityExecutionLock(
            JdbcTemplate jdbcTemplate,
            @Value("${coupon.integrity-check.lock-name:clutch:coupon-integrity-check}") String lockName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.lockName = lockName;
    }

    public boolean tryExecute(Runnable task) {
        Boolean result = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            if (!acquire(connection)) {
                return false;
            }
            try {
                task.run();
                return true;
            } finally {
                release(connection);
            }
        });
        return Boolean.TRUE.equals(result);
    }

    private boolean acquire(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ACQUIRE_SQL)) {
            statement.setString(1, lockName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        }
    }

    private void release(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(RELEASE_SQL)) {
            statement.setString(1, lockName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new SQLException("쿠폰 정합성 검증 잠금을 해제하지 못했습니다.");
                }
            }
        }
    }
}
