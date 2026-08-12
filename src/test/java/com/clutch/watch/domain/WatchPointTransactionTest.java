package com.clutch.watch.domain;

import com.clutch.watch.exception.WatchException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatchPointTransactionTest {

    /**
     * 정산 결과가 사용자, 세션, 경기 식별자와 최종 지급 포인트를 보존하는지 검증한다.
     */
    @Test
    void createsWatchPointTransaction() {
        WatchPointTransaction transaction = WatchPointTransaction.create(100L, 300L, 200L, 50L);

        assertThat(transaction.getUserId()).isEqualTo(100L);
        assertThat(transaction.getWatchSessionId()).isEqualTo(300L);
        assertThat(transaction.getEsportsMatchId()).isEqualTo(200L);
        assertThat(transaction.getAwardedPoint()).isEqualTo(50L);
    }

    /**
     * 음수 포인트가 거래 내역으로 생성되는 것을 거부하는지 검증한다.
     */
    @Test
    void rejectsNegativeAwardedPoint() {
        assertThatThrownBy(() -> WatchPointTransaction.create(100L, 300L, 200L, -1L))
                .isInstanceOf(WatchException.class)
                .hasMessage("지급 포인트는 음수일 수 없습니다.");
    }
}
