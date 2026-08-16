package com.clutch.lolesports.service;

import com.clutch.lolesports.entity.EsportsMatch;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LolesportsWatchAccrualEligibilityProviderTest {

    private static final long MATCH_ID = 200L;
    private static final String EXTERNAL_MATCH_ID = "external-match-200";
    private static final String GAME_ID = "game-1";

    @Mock
    private EsportsMatchRepository esportsMatchRepository;

    @Mock
    private DataCacheService dataCacheService;

    private LolesportsWatchAccrualEligibilityProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LolesportsWatchAccrualEligibilityProvider(
                esportsMatchRepository,
                dataCacheService
        );
    }

    @Test
    void allowsAccumulationOnlyWhileActiveSetIsNotFinished() {
        allowMatchLookup();
        when(dataCacheService.getLiveMatches()).thenReturn(List.of(liveMatch(GAME_ID)));
        when(dataCacheService.isGameInProgress(GAME_ID)).thenReturn(true);

        assertThat(provider.canAccumulate(MATCH_ID)).isTrue();
    }

    @Test
    void pausesBeforeSetStarts() {
        allowMatchLookup();
        when(dataCacheService.getLiveMatches()).thenReturn(List.of(liveMatch(null)));

        assertThat(provider.canAccumulate(MATCH_ID)).isFalse();
    }

    @Test
    void pausesImmediatelyAfterSetFinishes() {
        allowMatchLookup();
        when(dataCacheService.getLiveMatches()).thenReturn(List.of(liveMatch(GAME_ID)));
        when(dataCacheService.isGameInProgress(GAME_ID)).thenReturn(false);

        assertThat(provider.canAccumulate(MATCH_ID)).isFalse();
    }

    private void allowMatchLookup() {
        EsportsMatch match = new EsportsMatch(
                EXTERNAL_MATCH_ID,
                "league",
                "2026",
                "tournament",
                "block",
                LocalDateTime.of(2026, 8, 16, 12, 0),
                LocalDateTime.of(2026, 8, 16, 12, 0),
                "inProgress",
                3
        );
        ReflectionTestUtils.setField(match, "id", MATCH_ID);
        when(esportsMatchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
    }

    private DataCacheService.LiveMatch liveMatch(String activeGameId) {
        return new DataCacheService.LiveMatch(
                EXTERNAL_MATCH_ID,
                "block",
                "league",
                "2026-08-16T12:00:00Z",
                List.of(),
                List.of(),
                3,
                activeGameId
        );
    }
}
