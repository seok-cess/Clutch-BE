package com.clutch.betting.api;

import com.clutch.betting.dto.request.BetCreateRequest;
import com.clutch.betting.dto.response.BetCreateResponse;
import com.clutch.betting.dto.response.BettingEventResponse;
import com.clutch.betting.dto.response.MyBetResponse;
import com.clutch.betting.dto.response.UserBetResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 세트별 배팅 조회·등록 요청을 서비스 계층으로 전달한다. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class BettingController {

    private final BettingService bettingService;

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
