package com.clutch.betting.api;

import com.clutch.betting.api.request.BetCreateRequest;
import com.clutch.betting.api.request.BettingWinnerRecoveryRequest;
import com.clutch.betting.api.response.BetCreateResponse;
import com.clutch.betting.api.response.BettingCandidateResponse;
import com.clutch.betting.api.response.BettingEventResponse;
import com.clutch.betting.api.response.MyBetResponse;
import com.clutch.betting.api.response.UserBetResponse;
import com.clutch.betting.service.BettingService;
import com.clutch.wallet.web.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 세트별 배팅 후보·이벤트·사용자 배팅 조회와 등록 요청을 HTTP API로 제공한다.
 *
 * <p>운영자 승자 복구 엔드포인트도 배팅 이벤트를 직접 조작하지 않고 동일한 서비스
 * 유스케이스로 위임한다.</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class BettingController {

    private final BettingService bettingService;

    /**
     * 시작 전을 포함해 실제 OPEN 배팅 이벤트가 있는 매치를 배팅 카드용으로 반환한다.
     *
     * @return 현재 노출 가능한 배팅 후보 매치 목록
     */
    @GetMapping("/betting-candidates")
    public ResponseEntity<List<BettingCandidateResponse>> getBettingCandidates() {
        return ResponseEntity.ok(
                bettingService.findBettingCandidates().stream()
                        .map(BettingCandidateResponse::from)
                        .toList()
        );
    }

    /**
     * 자동 승자 판정이 불가능한 종료 이벤트에 운영자가 확인한 승자를 기록하고 즉시 정산한다.
     *
     * @param bettingEventId 승자를 복구할 배팅 이벤트 ID
     * @param request 운영자가 확인한 승리 팀 ID
     * @return 처리 완료를 나타내는 204 응답
     */
    @PutMapping("/admin/betting-events/{bettingEventId}/winner")
    public ResponseEntity<Void> recoverWinner(
            @PathVariable @Positive Long bettingEventId,
            @Valid @RequestBody BettingWinnerRecoveryRequest request
    ) {
        bettingService.recoverWinnerAndSettle(bettingEventId, request.winnerTeamId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 특정 매치에서 현재 노출할 배팅 이벤트와 내 배팅을 조회한다.
     *
     * @param userId X-User-Id 헤더에서 식별한 요청 사용자 ID
     * @param externalMatchId 외부 매치 ID
     * @return 현재 배팅 이벤트 응답
     */
    @GetMapping("/matches/{externalMatchId}/betting-events/current")
    public ResponseEntity<BettingEventResponse> getCurrentEvent(
            @CurrentUserId Long userId,
            @PathVariable @NotBlank String externalMatchId
    ) {
        return ResponseEntity.ok(BettingEventResponse.from(
                bettingService.getCurrentEvent(externalMatchId, userId)
        ));
    }

    /**
     * 사용자의 세트 승리 팀 선택과 배팅 금액을 등록한다.
     *
     * @param userId X-User-Id 헤더에서 식별한 배팅 등록 사용자 ID
     * @param bettingEventId 배팅 이벤트 ID
     * @param request 선택 팀과 배팅 금액
     * @return 등록 사용자 ID를 포함한 사용자 배팅 응답
     */
    @PostMapping("/betting-events/{bettingEventId}/bets")
    public ResponseEntity<BetCreateResponse> placeBet(
            @CurrentUserId Long userId,
            @PathVariable @Positive Long bettingEventId,
            @Valid @RequestBody BetCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BetCreateResponse.from(
                bettingService.place(
                        userId,
                        bettingEventId,
                        request.selectedTeamId(),
                        request.amount()
                )
        ));
    }

    /**
     * 특정 이벤트에 등록한 현재 사용자의 배팅을 조회한다.
     *
     * @param userId X-User-Id 헤더에서 식별한 조회 사용자 ID
     * @param bettingEventId 배팅 이벤트 ID
     * @return 조회 사용자 ID를 포함한 배팅 상세 응답
     */
    @GetMapping("/betting-events/{bettingEventId}/bets/me")
    public ResponseEntity<UserBetResponse> getMyBet(
            @CurrentUserId Long userId,
            @PathVariable @Positive Long bettingEventId
    ) {
        return ResponseEntity.ok(UserBetResponse.from(
                bettingService.getMyBet(bettingEventId, userId)
        ));
    }

    /**
     * 현재 사용자가 등록한 전체 배팅을 최신 순서로 조회한다.
     *
     * @param userId X-User-Id 헤더에서 식별한 조회 사용자 ID
     * @return 경기·세트 정보가 포함된 사용자 배팅 목록
     */
    @GetMapping("/users/me/bets")
    public ResponseEntity<List<MyBetResponse>> getMyBets(@CurrentUserId Long userId) {
        return ResponseEntity.ok(
                bettingService.getMyBets(userId).stream()
                        .map(MyBetResponse::from)
                        .toList()
        );
    }
}
