package com.clutch.lolesports.source;

import com.clutch.lolesports.service.HistoricalGameService;
import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.replay.service.ReplayControlException;
import com.clutch.replay.service.ReplayControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/** 운영자 요청에 따라 외부 API 소스를 전환한다. */
@Service
@ConditionalOnProperty(prefix = "external-source", name = "enabled", havingValue = "true")
public class ExternalSourceSwitchService {

    private static final Logger log = LoggerFactory.getLogger(ExternalSourceSwitchService.class);

    private final ExternalSourceState state;
    private final ReplayControlService replayControlService;
    private final PollingScheduler pollingScheduler;
    private final HistoricalGameService historicalGameService;
    private final Executor sourceRefreshExecutor;

    public ExternalSourceSwitchService(
            ExternalSourceState state,
            ReplayControlService replayControlService,
            PollingScheduler pollingScheduler,
            HistoricalGameService historicalGameService,
            @Qualifier("applicationTaskExecutor") Executor sourceRefreshExecutor
    ) {
        this.state = state;
        this.replayControlService = replayControlService;
        this.pollingScheduler = pollingScheduler;
        this.historicalGameService = historicalGameService;
        this.sourceRefreshExecutor = sourceRefreshExecutor;
    }

    public ExternalSourceStatus currentStatus() {
        return new ExternalSourceStatus(state.mode());
    }

    /**
     * 소스를 전환하고 이전 소스의 인메모리 상태를 제거한다.
     * STUB 전환은 fixture 재생을 시작하지 않는다. 새 test 경기는 별도 replay 시작 API가 담당한다.
     */
    public ExternalSourceStatus switchTo(ExternalSourceMode target) {
        if (target == null) {
            throw new IllegalArgumentException("전환할 외부 소스는 필수입니다.");
        }

        boolean changed = state.withWriteLock(() -> switchUnderLock(target));
        if (changed && target == ExternalSourceMode.REAL) {
            // 외부 API 워밍업은 응답 이후에 실행한다. 이 요청에서 네트워크 호출을 기다리면
            // 모드는 이미 REAL인데 운영자 화면은 전환 중으로 오래 남을 수 있다.
            sourceRefreshExecutor.execute(this::refreshRealSourceInBackground);
        }
        return currentStatus();
    }

    private void refreshRealSourceInBackground() {
        state.withReadLock(() -> {
            // 작업이 대기하는 사이 다시 STUB으로 전환됐다면 이전 요청의 워밍업은 실행하지 않는다.
            if (state.mode() != ExternalSourceMode.REAL) {
                return;
            }
            pollingScheduler.pollMeta();
            pollingScheduler.pollLiveMatches();
        });
    }

    private boolean switchUnderLock(ExternalSourceMode target) {
        ExternalSourceMode current = state.mode();
        if (current == target) {
            return false;
        }
        if (target == ExternalSourceMode.STUB) {
            verifyReplayServer();
        }

        state.changeMode(target);
        pollingScheduler.resetForExternalSourceChange();
        historicalGameService.resetForExternalSourceChange();
        log.info("외부 데이터 소스 전환: {} → {}", current, target);
        return true;
    }

    private void verifyReplayServer() {
        try {
            replayControlService.status();
        } catch (ReplayControlException exception) {
            throw new ExternalSourceSwitchException(
                    "replay 스텁 서버 상태를 확인할 수 없어 STUB 소스로 전환하지 않았습니다.",
                    exception
            );
        }
    }
}
