package com.clutch.user.service;

import com.clutch.user.exception.UserNotFoundException;
import com.clutch.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class UserQueryServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserQueryService service = new UserQueryService(userRepository);

    @Test
    void returnsCurrentPoint() {
        given(userRepository.findPointById(10L)).willReturn(Optional.of(12_000L));

        long point = service.getPoint(10L);

        assertThat(point).isEqualTo(12_000L);
    }

    @Test
    void rejectsUnknownUser() {
        given(userRepository.findPointById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPoint(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }
}
