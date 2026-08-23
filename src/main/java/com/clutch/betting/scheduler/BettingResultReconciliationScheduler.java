package com.clutch.betting.scheduler;

import com.clutch.betting.service.BettingResultReconciliationService;
import com.clutch.lolesports.source.ExternalSourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 라이브 목록에서 빠진 종료 세트의 공식 결과를 주기적으로 재확인한다.
 *
 * <p>기본 1분 주기는 외부 API의 수 분 결과 반영 지연을 흡수하면서도, 결과가 확인된 뒤
 * 기존 1초 정산 주기에서 즉시 포인트 정산이 이어지도록 한다.</p>
 */
@Component
public class BettingResultReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(BettingResultReconciliationScheduler.class);

    private final BettingResultReconciliationService reconciliationService;
    private final ExternalSourceState sourceState;

    public BettingResultReconciliationScheduler(
            BettingResultReconciliationService reconciliationService,
            ExternalSourceState sourceState
    ) {
        this.reconciliationService = reconciliationService;
        this.sourceState = sourceState;
    }

    @Scheduled(fixedDelayString = "${betting.result-reconciliation-interval:1m}")
    public void reconcilePendingResults() {
        try {
            sourceState.withReadLock(reconciliationService::reconcilePendingResults);
        } catch (RuntimeException exception) {
            log.warn("배팅 결과 재조회 작업 실패: {}", exception.toString());
        }
    }
}
