package com.clutch.betting.api;

import com.clutch.betting.domain.BetPointTransactionType;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.live.LiveBettingDataProvider;
import com.clutch.betting.live.LiveBettingDataProvider.LiveMatchSnapshot;
import com.clutch.betting.live.LiveBettingDataProvider.SetSnapshot;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.betting.scheduler.BettingScheduler;
import com.clutch.betting.service.BetSettlementService;
import com.clutch.betting.service.BettingEventSynchronizationService;
import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.user.domain.User;
import com.clutch.user.domain.UserRole;
import com.clutch.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BettingFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BettingEventRepository eventRepository;

    @Autowired
    private UserBetRepository userBetRepository;

    @Autowired
    private BetPointTransactionRepository transactionRepository;

    @Autowired
    private BetSettlementService settlementService;

    @Autowired
    private BettingEventSynchronizationService synchronizationService;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private LiveBettingDataProvider liveBettingDataProvider;

    @MockitoBean
    private PollingScheduler pollingScheduler;

    @MockitoBean
    private BettingScheduler bettingScheduler;

    private User user;
    private BettingEvent event;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now(java.time.Clock.systemUTC());
        user = User.create(UserRole.USER, "betting-flow@example.com");
        user.changePoint(5_000L);
        userRepository.saveAndFlush(user);
        event = BettingEvent.open(
                "bet-flow-match",
                1,
                "team-a",
                "team-b",
                now.minusMinutes(1),
                now.plusMinutes(19)
        );
        event.attachGame("bet-flow-game-1");
        eventRepository.saveAndFlush(event);
        given(liveBettingDataProvider.isAcceptingBets(any(), any(), anyInt())).willReturn(true);
    }

    @Test
    void placesAndSettlesWinningBetThroughHttpBoundary() throws Exception {
        mockMvc.perform(post("/api/betting-events/{eventId}/bets", event.getId())
                        .header("X-User-Id", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"selectedTeamId":"team-a","amount":1000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remainingPoint").value(4_000));

        eventRepository.findById(event.getId()).orElseThrow().recordWinner("team-a");
        eventRepository.flush();
        settlementService.settle(event.getId());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/betting-events/{eventId}/bets/me", event.getId())
                        .header("X-User-Id", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WON"))
                .andExpect(jsonPath("$.currentPoint").value(4_900));

        Long userBetId = userBetRepository
                .findByBettingEventIdAndUserId(event.getId(), user.getId())
                .orElseThrow()
                .getId();
        assertThat(transactionRepository.existsByUserBetIdAndTransactionType(
                userBetId,
                BetPointTransactionType.STAKE
        )).isTrue();
        assertThat(transactionRepository.existsByUserBetIdAndTransactionType(
                userBetId,
                BetPointTransactionType.PAYOUT
        )).isTrue();
    }

    @Test
    void finishedSetOpensOnlyOneNextSetEvent() {
        LocalDateTime now = LocalDateTime.now(java.time.Clock.systemUTC());
        LiveMatchSnapshot snapshot = new LiveMatchSnapshot(
                "bet-flow-match",
                now,
                List.of("team-a", "team-b"),
                List.of(new SetSnapshot(
                        "bet-flow-game-1",
                        1,
                        null,
                        false,
                        true,
                        now,
                        "team-a"
                )),
                false
        );

        synchronizationService.synchronizeMatch(snapshot);
        synchronizationService.synchronizeMatch(snapshot);
        entityManager.flush();
        entityManager.clear();

        BettingEvent nextEvent = eventRepository
                .findByExternalMatchIdAndSetNumber("bet-flow-match", 2)
                .orElseThrow();
        assertThat(nextEvent.getStatus()).isEqualTo(BettingEventStatus.OPEN);
        assertThat(eventRepository.findAll().stream()
                .filter(saved -> saved.getExternalMatchId().equals("bet-flow-match")))
                .hasSize(2);
    }
}
