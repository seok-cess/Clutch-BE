package com.clutch.lolesports.repository;

import com.clutch.lolesports.entity.GamePlayerStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GamePlayerStatRepository extends JpaRepository<GamePlayerStat, Long> {

    List<GamePlayerStat> findByGameIdOrderByParticipantNoAsc(Long gameId);

    void deleteByGameId(Long gameId);
}
