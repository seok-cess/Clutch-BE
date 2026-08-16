package com.clutch.betting.domain;

import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BettingDomainTest {

    @Test
    void opensSetBettingEvent() {
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 14, 10, 0);

        BettingEvent event = BettingEvent.open(
                "match-1", 1, "team-a", "team-b", openedAt, openedAt.plusMinutes(20)
        );

        assertThat(event.getExternalMatchId()).isEqualTo("match-1");
        assertThat(event.getSetNumber()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(BettingEventStatus.OPEN);
        assertThat(event.getOpenedAt()).isEqualTo(openedAt);
        assertThat(event.hasParticipant("team-a")).isTrue();
        assertThat(event.hasParticipant("team-c")).isFalse();
    }

    @Test
    void rejectsInvalidBettingEventParticipants() {
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 14, 10, 0);

        LocalDateTime closesAt = openedAt.plusMinutes(20);

        assertThatThrownBy(() -> BettingEvent.open(
                "match-1", 0, "team-a", "team-b", openedAt, closesAt
        ))
                .isInstanceOfSatisfying(BettingException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(BettingErrorCode.INVALID_SET_NUMBER));
        assertThatThrownBy(() -> BettingEvent.open(
                "match-1", 1, "team-a", "team-a", openedAt, closesAt
        ))
                .isInstanceOfSatisfying(BettingException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(BettingErrorCode.DUPLICATE_TEAM_OPTIONS));
    }

    @Test
    void acceptsBetsOnlyInsideHalfOpenPeriod() {
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 14, 9, 40);
        LocalDateTime closesAt = LocalDateTime.of(2026, 8, 14, 10, 1);
        BettingEvent event = BettingEvent.open(
                "match-1", 1, "team-a", "team-b", openedAt, closesAt
        );

        assertThat(event.isOpenAt(openedAt.minusNanos(1))).isFalse();
        assertThat(event.isOpenAt(openedAt)).isTrue();
        assertThat(event.isOpenAt(closesAt.minusNanos(1))).isTrue();
        assertThat(event.isOpenAt(closesAt)).isFalse();
    }

    @Test
    void rejectsEventWithoutValidBettingPeriod() {
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 14, 9, 40);

        assertThatThrownBy(() -> BettingEvent.open(
                "match-1", 1, "team-a", "team-b", openedAt, null
        )).isInstanceOfSatisfying(
                BettingException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(BettingErrorCode.EVENT_CLOSES_AT_REQUIRED)
        );
        assertThatThrownBy(() -> BettingEvent.open(
                "match-1", 1, "team-a", "team-b", openedAt, openedAt
        )).isInstanceOfSatisfying(
                BettingException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(BettingErrorCode.INVALID_BETTING_PERIOD)
        );
    }

    @Test
    void placesBetWithinAllowedAmount() {
        UserBet bet = UserBet.place(10L, 20L, "team-a", 1_000L);

        assertThat(bet.getBettingEventId()).isEqualTo(10L);
        assertThat(bet.getUserId()).isEqualTo(20L);
        assertThat(bet.getStatus()).isEqualTo(UserBetStatus.PLACED);
    }

    @Test
    void rejectsBetOutsideAllowedAmount() {
        assertThatThrownBy(() -> UserBet.place(10L, 20L, "team-a", 999L))
                .isInstanceOfSatisfying(BettingException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(BettingErrorCode.BET_AMOUNT_OUT_OF_RANGE));
        assertThatThrownBy(() -> UserBet.place(10L, 20L, "team-a", 100_001L))
                .isInstanceOfSatisfying(BettingException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(BettingErrorCode.BET_AMOUNT_OUT_OF_RANGE));
    }

    @Test
    void createsPointTransactionsWithSignedDeltas() {
        BetPointTransaction stake = BetPointTransaction.stake(1L, 1_000L);
        BetPointTransaction payout = BetPointTransaction.payout(1L, 2_000L);
        BetPointTransaction refund = BetPointTransaction.refund(1L, 1_000L);

        assertThat(stake.getPointDelta()).isEqualTo(-1_000L);
        assertThat(payout.getPointDelta()).isEqualTo(2_000L);
        assertThat(refund.getPointDelta()).isEqualTo(1_000L);
    }

    @Test
    void attachesSetWithoutChangingFixedBettingPeriod() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                1,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0),
                LocalDateTime.of(2026, 8, 14, 10, 20)
        );

        event.attachGame("game-1");

        assertThat(event.getExternalGameId()).isEqualTo("game-1");
        assertThat(event.getClosesAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 20));
        assertThat(event.closeIfExpired(LocalDateTime.of(2026, 8, 14, 10, 20))).isTrue();
        assertThat(event.getStatus()).isEqualTo(BettingEventStatus.CLOSED);
    }

    @Test
    void recordsOnlyParticipantAsWinner() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                1,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0),
                LocalDateTime.of(2026, 8, 14, 10, 20)
        );

        event.recordWinner("team-a");

        assertThat(event.getWinnerExternalTeamId()).isEqualTo("team-a");
        assertThat(event.getStatus()).isEqualTo(BettingEventStatus.CLOSED);
        assertThatThrownBy(() -> event.recordWinner("team-c"))
                .isInstanceOfSatisfying(BettingException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(BettingErrorCode.WINNER_NOT_PARTICIPANT));
    }

    @Test
    void keepsFirstConfirmedWinnerAndTerminalSnapshot() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                1,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0),
                LocalDateTime.of(2026, 8, 14, 10, 20)
        );
        event.attachGame("game-1");
        event.recordWinner("team-a");
        event.recordWinner("team-b");
        event.cancel();
        event.settle();
        event.attachGame("game-changed");

        assertThat(event.getWinnerExternalTeamId()).isEqualTo("team-a");
        assertThat(event.getStatus()).isEqualTo(BettingEventStatus.SETTLED);
        assertThat(event.getExternalGameId()).isEqualTo("game-1");
        assertThat(event.getClosesAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 20));
    }

    @Test
    void settlesEventAndUserBets() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                1,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0),
                LocalDateTime.of(2026, 8, 14, 10, 20)
        );
        UserBet wonBet = UserBet.place(1L, 10L, "team-a", 1_000L);
        UserBet lostBet = UserBet.place(1L, 20L, "team-b", 1_000L);

        event.recordWinner("team-a");
        wonBet.win();
        lostBet.lose();
        event.settle();

        assertThat(event.getStatus()).isEqualTo(BettingEventStatus.SETTLED);
        assertThat(wonBet.getStatus()).isEqualTo(UserBetStatus.WON);
        assertThat(lostBet.getStatus()).isEqualTo(UserBetStatus.LOST);
    }
}
