package com.clutch.lolesports.repository;

import com.clutch.lolesports.entity.MatchTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MatchTeamRepository extends JpaRepository<MatchTeam, Long> {

    List<MatchTeam> findByMatchIdOrderByDisplayOrderAsc(Long matchId);

    List<MatchTeam> findByMatchIdIn(List<Long> matchIds);

    List<MatchTeam> findByExternalTeamIdIn(Collection<String> externalTeamIds);
}
