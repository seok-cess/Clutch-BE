package com.clutch.betting.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BettingDomainTest {

    @Test
    void opensSetBettingEvent() {
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 14, 10, 0);

        BettingEvent event = BettingEvent.open("match-1", 1, "team-a", "team-b", openedAt);

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

        assertThatThrownBy(() -> BettingEvent.open("match-1", 0, "team-a", "team-b", openedAt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BettingEvent.open("match-1", 1, "team-a", "team-a", openedAt))
                .isInstanceOf(IllegalArgumentException.class);
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
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserBet.place(10L, 20L, "team-a", 100_001L))
                .isInstanceOf(IllegalArgumentException.class);
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
    void attachesSetAndClosesTwoMinutesAfterStart() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                1,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0)
        );

        event.attachGame(
                "game-1",
                LocalDateTime.of(2026, 8, 14, 10, 1),
                Duration.ofMinutes(2)
        );

        assertThat(event.getExternalGameId()).isEqualTo("game-1");
        assertThat(event.getClosesAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 3));
        assertThat(event.closeIfExpired(LocalDateTime.of(2026, 8, 14, 10, 3))).isTrue();
        assertThat(event.getStatus()).isEqualTo(BettingEventStatus.CLOSED);
    }

    @Test
    void recordsOnlyParticipantAsWinner() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                1,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0)
        );

        event.recordWinner("team-a");

        assertThat(event.getWinnerExternalTeamId()).isEqualTo("team-a");
        assertThat(event.getStatus()).isEqualTo(BettingEventStatus.CLOSED);
        assertThatThrownBy(() -> event.recordWinner("team-c"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settlesEventAndUserBets() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                1,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0)
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
