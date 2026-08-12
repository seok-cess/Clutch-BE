package com.clutch.lolesports.repository;

import com.clutch.lolesports.entity.GameTimelinePoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameTimelinePointRepository extends JpaRepository<GameTimelinePoint, Long> {

    List<GameTimelinePoint> findByGameIdOrderByGameTimeSecondsAsc(Long gameId);

    void deleteByGameId(Long gameId);
}
