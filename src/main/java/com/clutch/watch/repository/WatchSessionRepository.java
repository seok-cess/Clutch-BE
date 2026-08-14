package com.clutch.watch.repository;

import com.clutch.watch.domain.WatchSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 시청 세션 저장소.
 */
public interface WatchSessionRepository extends JpaRepository<WatchSession, Long> {

    Optional<WatchSession> findBySessionKey(String sessionKey);
}
