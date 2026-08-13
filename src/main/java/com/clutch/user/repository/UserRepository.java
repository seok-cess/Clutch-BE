package com.clutch.user.repository;

import com.clutch.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 저장소.
 */
public interface UserRepository extends JpaRepository<User, Long> {
}
