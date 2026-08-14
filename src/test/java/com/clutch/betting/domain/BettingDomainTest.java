package com.clutch.betting.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

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
}
