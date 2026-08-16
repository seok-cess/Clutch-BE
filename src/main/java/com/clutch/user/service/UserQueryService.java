package com.clutch.user.service;

import com.clutch.user.exception.UserNotFoundException;
import com.clutch.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 본인 정보 조회 유스케이스를 제공한다. */
@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;

    /**
     * 사용자의 현재 보유 포인트를 조회한다.
     *
     * @param userId 사용자 ID
     * @return 조회 시점의 보유 포인트
     * @throws UserNotFoundException 사용자를 찾을 수 없을 때
     */
    @Transactional(readOnly = true)
    public long getPoint(Long userId) {
        return userRepository.findPointById(userId)
                .orElseThrow(UserNotFoundException::new);
    }
}
