package com.clutch.user.dto.response;

import com.clutch.user.dto.PointTransactionHistory;
import com.clutch.user.dto.PointTransactionType;

import java.time.LocalDateTime;

/** 현재 사용자의 포인트 증감 이력 응답이다. */
public record PointTransactionHistoryResponse(
        String transactionId,
        PointTransactionType type,
        long pointDelta,
        LocalDateTime createdAt
) {

    public static PointTransactionHistoryResponse from(PointTransactionHistory history) {
        return new PointTransactionHistoryResponse(
                history.transactionId(),
                history.type(),
                history.pointDelta(),
                history.createdAt()
        );
    }
}
