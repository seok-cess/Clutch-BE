package com.clutch.user.api;

import com.clutch.user.api.dto.AdminUserListResponse;
import com.clutch.user.service.AdminUserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 회원 조회. 응답의 개인정보는 모두 마스킹된다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserQueryService adminUserQueryService;

    /**
     * 회원 목록을 커서 순으로 조회한다.
     *
     * @param cursor 이전 페이지의 마지막 회원 ID, 첫 조회 시 {@code null}
     * @param size 한 번에 조회할 회원 수 (최대 100)
     */
    @GetMapping
    public ResponseEntity<AdminUserListResponse> findUsers(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminUserQueryService.findUsers(cursor, size));
    }
}
