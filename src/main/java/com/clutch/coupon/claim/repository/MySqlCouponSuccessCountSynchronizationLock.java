package com.clutch.coupon.claim.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** MySQL named lock으로 성공 수량 집계의 다중 인스턴스 중복 실행을 막는다. */
@Repository
public class MySqlCouponSuccessCountSynchronizationLock
        implements CouponSuccessCountSynchronizationLock {

    private static final String ACQUIRE_SQL = "SELECT GET_LOCK(?, 0)";
    private static final String RELEASE_SQL = "SELECT RELEASE_LOCK(?)";

    private final JdbcTemplate jdbcTemplate;
    private final String lockName;

    public MySqlCouponSuccessCountSynchronizationLock(
            JdbcTemplate jdbcTemplate,
            @Value("${coupon.success-count-sync.lock-name:"
                    + "clutch:coupon-success-count-synchronization}")
            String lockName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.lockName = lockName;
    }

    @Override
    public boolean tryExecute(Runnable task) {
        Boolean executed = jdbcTemplate.execute(
                (ConnectionCallback<Boolean>) connection ->
                        executeWithConnection(connection, task)
        );
        return Boolean.TRUE.equals(executed);
    }

    private boolean executeWithConnection(
            Connection connection,
            Runnable task
    ) throws SQLException {
        if (!acquire(connection)) {
            return false;
        }

        try {
            task.run();
            return true;
        } finally {
            release(connection);
        }
    }

    private boolean acquire(Connection connection) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(ACQUIRE_SQL)) {
            statement.setString(1, lockName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        }
    }

    private void release(Connection connection) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(RELEASE_SQL)) {
            statement.setString(1, lockName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new SQLException(
                            "쿠폰 성공 수량 동기화 잠금을 해제하지 못했습니다."
                    );
                }
            }
        }
    }
}
