package com.clutch.user.dto;

import java.time.LocalDateTime;

/** 리워드 화면에 표시할 사용자의 포인트 증감 원장 한 건이다. */
public record PointTransactionHistory(
        String transactionId,
        PointTransactionType type,
        long pointDelta,
        LocalDateTime createdAt
) {
}
