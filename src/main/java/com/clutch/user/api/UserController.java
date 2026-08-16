package com.clutch.user.api;

import com.clutch.user.dto.response.UserPointResponse;
import com.clutch.user.service.UserQueryService;
import com.clutch.wallet.web.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 현재 사용자의 기본 정보를 조회한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me")
public class UserController {

    private final UserQueryService userQueryService;

    /**
     * 현재 사용자의 보유 포인트를 조회한다.
     *
     * @param userId X-User-Id 헤더에서 식별한 사용자 ID
     * @return 사용자 ID와 현재 보유 포인트
     */
    @GetMapping("/points")
    public ResponseEntity<UserPointResponse> getPoint(@CurrentUserId Long userId) {
        return ResponseEntity.ok(new UserPointResponse(
                userId,
                userQueryService.getPoint(userId)
        ));
    }
}
