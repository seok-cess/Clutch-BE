package com.clutch.watch.domain;

import com.clutch.watch.exception.WatchException;
import com.clutch.watch.exception.WatchError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatchPointTransactionTest {

    /**
     * 수령 결과가 사용자, 세션, 회차, 경기 식별자와 최종 지급 포인트를 보존하는지 검증한다.
     */
    @Test
    void createsWatchPointTransaction() {
        WatchPointTransaction transaction = WatchPointTransaction.create(
                100L, 300L, 2L, 200L, 100L
        );

        assertThat(transaction.getUserId()).isEqualTo(100L);
        assertThat(transaction.getWatchSessionId()).isEqualTo(300L);
        assertThat(transaction.getRewardSequence()).isEqualTo(2L);
        assertThat(transaction.getEsportsMatchId()).isEqualTo(200L);
        assertThat(transaction.getAwardedPoint()).isEqualTo(100L);
    }

    /**
     * 음수 포인트가 거래 내역으로 생성되는 것을 거부하는지 검증한다.
     */
    @Test
    void rejectsNegativeAwardedPoint() {
        assertThatThrownBy(() -> WatchPointTransaction.create(100L, 300L, 1L, 200L, -1L))
                .isInstanceOf(WatchException.class)
                .hasMessage("지급 포인트는 음수일 수 없습니다.");
    }

    /**
     * 1보다 작은 포인트 수령 회차를 거부하는지 검증한다.
     */
    @Test
    void rejectsInvalidRewardSequence() {
        assertWatchError(
                () -> WatchPointTransaction.create(100L, 300L, 0L, 200L, 100L),
                WatchError.REWARD_SEQUENCE_INVALID
        );
    }

    /**
     * 포인트 거래에 필요한 식별자가 누락되면 각각 정의된 오류로 거부하는지 검증한다.
     */
    @Test
    void rejectsMissingRequiredIdentifiers() {
        assertWatchError(
                () -> WatchPointTransaction.create(null, 300L, 1L, 200L, 0L),
                WatchError.USER_ID_REQUIRED
        );
        assertWatchError(
                () -> WatchPointTransaction.create(100L, null, 1L, 200L, 0L),
                WatchError.WATCH_SESSION_ID_REQUIRED
        );
        assertWatchError(
                () -> WatchPointTransaction.create(100L, 300L, 1L, null, 0L),
                WatchError.MATCH_ID_REQUIRED
        );
    }

    /**
     * 실행 결과가 기대한 Watch 오류인지 검증한다.
     *
     * @param runnable 예외가 발생해야 하는 실행 코드
     * @param expectedError 기대하는 Watch 오류
     */
    private void assertWatchError(Runnable runnable, WatchError expectedError) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(WatchException.class,
                        exception -> assertThat(exception.getError()).isEqualTo(expectedError));
    }
}
