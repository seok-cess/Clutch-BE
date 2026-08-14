package com.clutch.betting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "bet_point_transaction",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bet_point_transaction_bet_type",
                columnNames = {"user_bet_id", "transaction_type"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BetPointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bet_point_transaction_id", nullable = false)
    private Long id;

    @Column(name = "user_bet_id", nullable = false)
    private Long userBetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private BetPointTransactionType transactionType;

    @Column(name = "point_delta", nullable = false)
    private long pointDelta;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private BetPointTransaction(
            Long userBetId,
            BetPointTransactionType transactionType,
            long pointDelta
    ) {
        if (userBetId == null) {
            throw new IllegalArgumentException("사용자 배팅 ID는 필수입니다.");
        }
        if (transactionType == null) {
            throw new IllegalArgumentException("포인트 거래 유형은 필수입니다.");
        }
        if (transactionType == BetPointTransactionType.STAKE && pointDelta >= 0) {
            throw new IllegalArgumentException("배팅 차감 포인트는 음수여야 합니다.");
        }
        if (transactionType != BetPointTransactionType.STAKE && pointDelta <= 0) {
            throw new IllegalArgumentException("지급 또는 환불 포인트는 양수여야 합니다.");
        }
        this.userBetId = userBetId;
        this.transactionType = transactionType;
        this.pointDelta = pointDelta;
    }

    public static BetPointTransaction stake(Long userBetId, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("배팅 금액은 양수여야 합니다.");
        }
        return new BetPointTransaction(userBetId, BetPointTransactionType.STAKE, -amount);
    }

    public static BetPointTransaction payout(Long userBetId, long amount) {
        return new BetPointTransaction(userBetId, BetPointTransactionType.PAYOUT, amount);
    }

    public static BetPointTransaction refund(Long userBetId, long amount) {
        return new BetPointTransaction(userBetId, BetPointTransactionType.REFUND, amount);
    }
}
