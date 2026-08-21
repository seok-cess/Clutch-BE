package com.clutch.user.service;

import com.clutch.user.api.dto.AdminUserListResponse;
import com.clutch.user.api.dto.AdminUserSummaryResponse;
import com.clutch.user.domain.User;
import com.clutch.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자 회원 조회.
 *
 * 응답에 담기는 개인정보는 DTO 변환 시점에 모두 마스킹된다
 * ({@link AdminUserSummaryResponse}). 서비스는 엔티티를 그대로 넘기지 않는다 —
 * 컨트롤러에서 엔티티를 직렬화하면 마스킹을 우회하게 된다.
 */
@Service
@RequiredArgsConstructor
public class AdminUserQueryService {

    /** 한 번에 가져올 수 있는 상한. 회원이 100만 건이라 무제한 조회를 허용하지 않는다 */
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;

    /**
     * 회원 목록을 ID 커서 순으로 조회한다.
     *
     * @param cursor 이전 페이지의 마지막 회원 ID, 첫 조회면 null
     * @param size 한 번에 조회할 회원 수
     */
    @Transactional(readOnly = true)
    public AdminUserListResponse findUsers(Long cursor, int size) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Slice<User> slice = userRepository.findSliceForAdmin(
                cursor, PageRequest.of(0, pageSize));
        List<User> users = slice.getContent();

        List<AdminUserSummaryResponse> responses = users.stream()
                .map(AdminUserSummaryResponse::from)
                .toList();
        Long nextCursor = slice.hasNext() && !users.isEmpty()
                ? users.getLast().getId()
                : null;

        return new AdminUserListResponse(responses, nextCursor, slice.hasNext());
    }
}
