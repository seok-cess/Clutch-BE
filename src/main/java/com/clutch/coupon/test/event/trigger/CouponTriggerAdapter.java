package com.clutch.coupon.test.event.trigger;

import com.clutch.coupon.contract.trigger.CouponMatchTrigger;
import com.clutch.coupon.contract.trigger.CouponTestMatch;
import com.clutch.coupon.contract.trigger.CouponTriggerPort;
import com.clutch.coupon.test.event.domain.CouponEventTrigger;
import com.clutch.coupon.test.event.service.CouponEventActivationService;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import com.clutch.lolesports.source.ExternalSourceMode;
import com.clutch.lolesports.source.ExternalSourceState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 경기 감지 결과를 쿠폰 이벤트 오픈으로 옮기는 어댑터.
 *
 * <p>감지 쪽은 외부 ID(피드가 주는 문자열)만 안다. 쿠폰 이벤트는 내부 PK 로
 * 경기를 가리키므로 여기서 한 번 변환한다.</p>
 *
 * <p>replay 재생(STUB) 중에는 예약된 테스트 경기로도 함께 시도한다.
 * replay 는 실행마다 경기 ID 를 새로 만들어 이벤트를 미리 걸어둘 수 없기 때문이다.</p>
 *
 * <p>폴링 루프에서 호출되므로 어떤 예외도 밖으로 내보내지 않는다 —
 * 쿠폰 오픈이 실패했다고 경기 데이터 수집까지 멈추면 안 된다.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CouponTriggerAdapter implements CouponTriggerPort {

    private final CouponEventActivationService activationService;
    private final EsportsMatchRepository matchRepository;
    private final ExternalSourceState sourceState;

    @Override
    public void fire(
            CouponMatchTrigger trigger,
            String externalMatchId,
            String externalGameId,
            Integer gameTimeSeconds
    ) {
        if (trigger == null) {
            return;
        }

        Long esportsMatchId = resolveMatchId(externalMatchId);
        if (esportsMatchId != null) {
            open(trigger, esportsMatchId, externalMatchId, externalGameId, gameTimeSeconds);
        }

        /*
         * replay 재생 중이면 예약된 테스트 경기로도 연다.
         *
         * replay 스텁은 실행마다 새 경기 ID(replay-<runId>-m1)를 만든다. 그래서
         * 재생 경기에 이벤트를 미리 걸어둘 수 없다 — 다음 실행이면 ID 가 달라진다.
         * 고정된 테스트 경기 ID 로 만들어 둔 이벤트가 재생 중 열리게 한다.
         *
         * 실제 소스(LIVE)에서는 절대 실행되지 않는다.
         */
        if (sourceState.mode() == ExternalSourceMode.STUB) {
            open(
                    trigger,
                    CouponTestMatch.SAMPLE_MATCH_ID,
                    externalMatchId,
                    externalGameId,
                    gameTimeSeconds
            );
        }
    }

    /** 외부 경기 ID 를 내부 PK 로 바꾼다. 아직 저장 전이면 {@code null} */
    private Long resolveMatchId(String externalMatchId) {
        if (externalMatchId == null || externalMatchId.isBlank()) {
            return null;
        }
        try {
            return matchRepository.findByExternalMatchId(externalMatchId)
                    .map(match -> match.getId())
                    .orElse(null);
        } catch (Exception exception) {
            log.warn("외부 경기 {} 조회 실패: {}", externalMatchId, exception.toString());
            return null;
        }
    }

    private void open(
            CouponMatchTrigger trigger,
            Long esportsMatchId,
            String externalMatchId,
            String externalGameId,
            Integer gameTimeSeconds
    ) {
        try {
            activationService.openByTrigger(
                    CouponEventTrigger.valueOf(trigger.name()),
                    esportsMatchId,
                    externalGameId,
                    gameTimeSeconds
            );
        } catch (DataIntegrityViolationException exception) {
            // sourceEventKey 유니크 충돌 — 같은 사건을 이미 열었다. 정상 동작이다
            log.debug("트리거 {} — 경기 {} 세트 {} 는 이미 열려 있다",
                    trigger, esportsMatchId, externalGameId);
        } catch (Exception exception) {
            log.warn("트리거 {} 처리 실패 (matchId={}, externalMatchId={}, gameId={}): {}",
                    trigger, esportsMatchId, externalMatchId, externalGameId,
                    exception.toString());
        }
    }
}
