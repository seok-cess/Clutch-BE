package com.clutch.user.api;

import com.clutch.user.dto.response.PointRankingResponse;
import com.clutch.user.dto.response.PointTransactionHistoryResponse;
import com.clutch.user.dto.response.MyPointRankingResponse;
import com.clutch.user.dto.response.UserPointResponse;
import com.clutch.user.dto.response.UserPointSummaryResponse;
import com.clutch.user.service.UserService;
import com.clutch.wallet.web.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 사용자 포인트 정보와 전체 포인트 순위를 조회한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    /**
     * 현재 사용자의 보유 포인트를 조회한다.
     *
     * @param userId X-User-Id 헤더에서 식별한 사용자 ID
     * @return 사용자 ID와 현재 보유 포인트
     */
    @GetMapping("/me/points")
    public ResponseEntity<UserPointResponse> getPoint(@CurrentUserId Long userId) {
        return ResponseEntity.ok(new UserPointResponse(
                userId,
                userService.getPoint(userId)
        ));
    }

    /** 현재 사용자의 포인트·승부예측 요약을 조회한다. */
    @GetMapping("/me/point-summary")
    public ResponseEntity<UserPointSummaryResponse> getPointSummary(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(UserPointSummaryResponse.from(
                userService.getPointSummary(userId)
        ));
    }

    /** 현재 사용자의 보유 포인트와 전체 순위를 조회한다. */
    @GetMapping("/me/point-ranking")
    public ResponseEntity<MyPointRankingResponse> getMyPointRanking(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(MyPointRankingResponse.from(
                userService.getMyPointRanking(userId)
        ));
    }

    /** 현재 사용자의 시청·승부예측 포인트 증감 이력을 최신 순으로 조회한다. */
    @GetMapping("/me/point-transactions")
    public ResponseEntity<List<PointTransactionHistoryResponse>> getPointTransactionHistory(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(userService.getPointTransactionHistory(userId).stream()
                .map(PointTransactionHistoryResponse::from)
                .toList());
    }

    /** 전체 사용자 보유 포인트 상위 10명을 조회한다. */
    @GetMapping("/point-rankings")
    public ResponseEntity<List<PointRankingResponse>> getPointRankings() {
        return ResponseEntity.ok(userService.getPointRankings().stream()
                .map(PointRankingResponse::from)
                .toList());
    }
}
