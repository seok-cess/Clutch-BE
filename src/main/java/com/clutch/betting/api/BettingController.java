package com.clutch.betting.api;

import com.clutch.betting.api.dto.BetCreateRequest;
import com.clutch.betting.api.dto.BetCreateResponse;
import com.clutch.betting.api.dto.BettingEventResponse;
import com.clutch.betting.api.dto.UserBetResponse;
import com.clutch.betting.service.BetPlacementService;
import com.clutch.betting.service.BetQueryService;
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

@Validated
@RestController
@RequestMapping("/api")
public class BettingController {

    private final BetPlacementService placementService;
    private final BetQueryService queryService;

    public BettingController(
            BetPlacementService placementService,
            BetQueryService queryService
    ) {
        this.placementService = placementService;
        this.queryService = queryService;
    }

    @GetMapping("/matches/{externalMatchId}/betting-events/current")
    public ResponseEntity<BettingEventResponse> getCurrentEvent(
            @CurrentUserId Long userId,
            @PathVariable @NotBlank String externalMatchId
    ) {
        return ResponseEntity.ok(BettingEventResponse.from(
                queryService.getCurrentEvent(externalMatchId, userId)
        ));
    }

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
