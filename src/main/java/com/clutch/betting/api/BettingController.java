package com.clutch.betting.api;

import com.clutch.betting.api.request.BetCreateRequest;
import com.clutch.betting.api.response.BetCreateResponse;
import com.clutch.betting.api.response.BettingEventResponse;
import com.clutch.betting.api.response.UserBetResponse;
import com.clutch.betting.service.placement.BetPlacementService;
import com.clutch.betting.service.query.BetQueryService;
import com.clutch.wallet.web.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 세트별 배팅 조회·등록 요청을 서비스 계층으로 전달한다. */
@Validated
@RestController
@RequestMapping("/api")
public class BettingController {

    private final BetPlacementService placementService;
    private final BetQueryService queryService;

    /**
     * 배팅 등록과 조회 유스케이스를 주입받는다.
     *
     * @param placementService 배팅 등록 서비스
     * @param queryService 배팅 조회 서비스
     */
    public BettingController(
            BetPlacementService placementService,
            BetQueryService queryService
    ) {
        this.placementService = placementService;
        this.queryService = queryService;
    }

    /**
     * 특정 매치에서 현재 노출할 배팅 이벤트와 내 배팅을 조회한다.
     *
     * @param userId 요청 사용자 ID
     * @param externalMatchId 외부 매치 ID
     * @return 현재 배팅 이벤트 응답
     */
    @GetMapping("/matches/{externalMatchId}/betting-events/current")
    public ResponseEntity<BettingEventResponse> getCurrentEvent(
            @CurrentUserId Long userId,
            @PathVariable @NotBlank String externalMatchId
    ) {
        return ResponseEntity.ok(BettingEventResponse.from(
                queryService.getCurrentEvent(externalMatchId, userId)
        ));
    }

    /**
     * 사용자의 세트 승리 팀 선택과 배팅 금액을 등록한다.
     *
     * @param userId 요청 사용자 ID
     * @param bettingEventId 배팅 이벤트 ID
     * @param request 선택 팀과 배팅 금액
     * @return 생성된 사용자 배팅 응답
     */
    @PostMapping("/betting-events/{bettingEventId}/bets")
    public ResponseEntity<BetCreateResponse> placeBet(
            @CurrentUserId Long userId,
            @PathVariable @Positive Long bettingEventId,
            @Valid @RequestBody BetCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BetCreateResponse.from(
                placementService.place(
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
     * @param userId 요청 사용자 ID
     * @param bettingEventId 배팅 이벤트 ID
     * @return 사용자 배팅 상세 응답
     */
    @GetMapping("/betting-events/{bettingEventId}/bets/me")
    public ResponseEntity<UserBetResponse> getMyBet(
            @CurrentUserId Long userId,
            @PathVariable @Positive Long bettingEventId
    ) {
        return ResponseEntity.ok(UserBetResponse.from(
                queryService.getMyBet(bettingEventId, userId)
        ));
    }
}
