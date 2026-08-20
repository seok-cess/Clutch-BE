package com.clutch.lolesports.source;

import com.clutch.lolesports.service.HistoricalGameService;
import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.replay.service.ReplayControlException;
import com.clutch.replay.service.ReplayControlService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

class ExternalSourceSwitchServiceTest {

    @Test
    void 스텁_전환_전에_replay_서버를_확인하고_캐시를_초기화한다() {
        ExternalSourceState state = state(ExternalSourceMode.REAL);
        ReplayControlService replay = mock(ReplayControlService.class);
        PollingScheduler polling = mock(PollingScheduler.class);
        HistoricalGameService historical = mock(HistoricalGameService.class);
        ExternalSourceSwitchService service = new ExternalSourceSwitchService(
                state, replay, polling, historical, Runnable::run);

        ExternalSourceStatus status = service.switchTo(ExternalSourceMode.STUB);

        assertEquals(ExternalSourceMode.STUB, status.mode());
        verify(replay).status();
        verify(polling).resetForExternalSourceChange();
        verify(historical).resetForExternalSourceChange();
        verify(polling, never()).pollMeta();
        verify(polling, never()).pollLiveMatches();
    }

    @Test
    void 실제_소스로_복귀하면_응답_후_캐시를_다시_채운다() {
        ExternalSourceState state = state(ExternalSourceMode.STUB);
        PollingScheduler polling = mock(PollingScheduler.class);
        AtomicReference<Runnable> refreshTask = new AtomicReference<>();
        ExternalSourceSwitchService service = new ExternalSourceSwitchService(
                state, mock(ReplayControlService.class), polling, mock(HistoricalGameService.class), refreshTask::set);

        ExternalSourceStatus status = service.switchTo(ExternalSourceMode.REAL);

        assertEquals(ExternalSourceMode.REAL, status.mode());
        verify(polling).resetForExternalSourceChange();
        verify(polling, never()).pollMeta();
        verify(polling, never()).pollLiveMatches();

        assertNotNull(refreshTask.get());
        refreshTask.get().run();

        verify(polling).pollMeta();
        verify(polling).pollLiveMatches();
    }

    @Test
    void replay_서버를_확인하지_못하면_스텁으로_전환하지_않는다() {
        ExternalSourceState state = state(ExternalSourceMode.REAL);
        ReplayControlService replay = mock(ReplayControlService.class);
        doThrow(new ReplayControlException("연결 실패")).when(replay).status();
        PollingScheduler polling = mock(PollingScheduler.class);
        HistoricalGameService historical = mock(HistoricalGameService.class);
        ExternalSourceSwitchService service = new ExternalSourceSwitchService(
                state, replay, polling, historical, Runnable::run);

        assertThrows(ExternalSourceSwitchException.class, () -> service.switchTo(ExternalSourceMode.STUB));

        assertEquals(ExternalSourceMode.REAL, state.mode());
        verify(polling, never()).resetForExternalSourceChange();
        verify(historical, never()).resetForExternalSourceChange();
    }

    private ExternalSourceState state(ExternalSourceMode initialMode) {
        return new ExternalSourceState(new ExternalSourceProperties(
                true, initialMode, "http://stub-esports", "http://stub-live"));
    }
}
