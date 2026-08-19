package com.clutch.betting.api;

import com.clutch.betting.dto.request.BettingWinnerRecoveryRequest;
import com.clutch.betting.service.BettingResultRecoveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 자동 승자 판정이 불가능한 배팅 이벤트를 운영자가 복구하는 API다. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/betting-events")
public class BettingAdminController {

    private final BettingResultRecoveryService recoveryService;

    /** 확인된 승자를 기록하고 미정산 사용자 배팅을 즉시 정산한다. */
    @PutMapping("/{bettingEventId}/winner")
    public ResponseEntity<Void> recoverWinner(
            @PathVariable @Positive Long bettingEventId,
            @Valid @RequestBody BettingWinnerRecoveryRequest request
    ) {
        recoveryService.recoverAndSettle(bettingEventId, request.winnerTeamId());
        return ResponseEntity.noContent().build();
    }
}
