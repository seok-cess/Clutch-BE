package com.clutch.lolesports.api;

import java.util.List;

/**
 * 프론트에 노출하는 가공 응답 DTO 모음.
 */
public final class ApiDtos {

    private ApiDtos() {
    }

    // ---- /api/schedule ----

    public record ScheduleItem(
            String startTime,
            String state,        // unstarted | inProgress | completed
            String blockName,
            String matchId,
            Integer bestOf,
            List<ScheduleTeam> teams
    ) {
    }

    public record ScheduleTeam(
            String id,           // 스코어보드의 esportsTeamId 와 매칭해 진영(블루/레드) 판별
            String name,
            String code,
            String image,
            String outcome,      // win | loss | null
            Integer gameWins,
            Integer wins,        // 시즌 전적
            Integer losses
    ) {
    }

    // ---- 전적 (최근 폼 / 상대 전적) ----

    /** 한 팀 관점에서 본 완료 경기 하나 */
    public record RecentMatch(
            String startTime,
            String opponentCode,
            String opponentName,
            String outcome,        // win | loss
            Integer gameWins,      // 내 세트 득점
            Integer opponentGameWins
    ) {
    }

    /** 두 팀의 상대 전적 */
    public record HeadToHead(
            String teamA,
            String teamB,
            int winsA,
            int winsB,
            List<RecentMatch> meetings   // 최신순
    ) {
    }

    // ---- /api/stats/* (시즌 누적 집계) ----

    /**
     * 시즌 누적 KDA 순위.
     *
     * @param seasonKey  집계 대상 시즌. 적재된 시즌이 없으면 null
     * @param totalGames 집계에 쓰인 완료 세트 수
     */
    public record PlayerKdaBoard(String seasonKey, int totalGames, List<PlayerKdaRow> players) {
    }

    /**
     * @param teamCode 팀 이동이 있으면 팀별로 행이 나뉜다
     * @param kda      (킬 + 어시스트) / 데스. 데스가 0이면 1로 나눈다
     */
    public record PlayerKdaRow(
            int rank,
            String summonerName,
            String teamCode,
            int games,
            int kills,
            int deaths,
            int assists,
            double kda
    ) {
    }

    /** 시즌 챔피언 픽률과 승률. 밴 데이터는 수집하지 않아 밴픽률은 제공하지 않는다 */
    public record ChampionBoard(String seasonKey, int totalGames, List<ChampionRow> champions) {
    }

    /**
     * @param pickRate     picks / totalGames (0~1). 완료 세트가 없으면 null
     * @param wins         승자가 확정된 픽 중 승리 수. 확정된 픽이 없으면 null
     * @param decidedPicks 세트 승자가 확정된 픽 수. 승자는 종료 약 5분 뒤 확정된다
     * @param winRate      wins / decidedPicks (0~1). 확정된 픽이 없으면 null
     */
    public record ChampionRow(
            int rank,
            String championId,
            int picks,
            Double pickRate,
            Integer wins,
            int decidedPicks,
            Double winRate
    ) {
    }

    // ---- /api/standings ----

    public record StandingsSection(
            String stageName,    // 예: "그룹", "플레이-인"
            String sectionName,  // 예: "레전드 그룹"
            List<RankingRow> rankings
    ) {
    }

    public record RankingRow(Integer ordinal, List<RankedTeam> teams) {
    }

    public record RankedTeam(String name, String code, String image, Integer wins, Integer losses) {
    }

    /**
     * 리그 순위표 한 행 (매치 기준).
     *
     * @param setDiff 딴 세트 - 내준 세트
     * @param winRate 0~1. 승패가 하나도 없으면 null
     */
    public record TeamStandingRow(
            Integer rank,
            String teamCode,
            String teamName,
            String teamImageUrl,
            int games,
            int wins,
            int losses,
            int setsWon,
            int setsLost,
            int setDiff,
            Double winRate
    ) {
    }

    /**
     * 순위표 한 그룹.
     *
     * LCK 는 정규시즌을 레전드·라이즈 두 그룹으로 나눠 운영한다. 그룹 편성은
     * 소스의 getStandingsV3 가 주므로 우리가 판단하지 않는다.
     *
     * @param groupName 그룹 없이 단일 순위면 null
     */
    public record TeamStandingsGroup(String groupName, List<TeamStandingRow> rows) {
    }

    /** /api/standings/teams 응답 */
    public record TeamStandingsBoard(String seasonKey, List<TeamStandingsGroup> groups) {
    }

    // ---- /api/live ----

    public record LiveSummary(boolean live, List<LiveMatchItem> matches) {
    }

    /**
     * 진행중(으로 노출되는) 매치 하나.
     *
     * 소스의 getLive 는 매치가 끝나도 한동안 진행중으로 남고, 세트 state 도 실제
     * 종료보다 약 5분 늦다. 그래서 종료 여부는 소스 상태를 그대로 쓰지 않고
     * 우리가 판정해 matchFinished / matchWinnerTeamId 로 내린다.
     * 스코어(gameWins)만 소스 값을 그대로 쓴다.
     *
     * @param matchFinished      과반 세트 승리로 매치가 끝났는지
     * @param matchWinnerTeamId  매치 최종 승리 팀. 미확정이면 null
     */
    public record LiveMatchItem(
            String matchId,
            String leagueName,
            String blockName,
            String startTime,
            Integer bestOf,
            boolean matchFinished,
            String matchWinnerTeamId,
            List<ScheduleTeam> teams,
            List<GameItem> games,
            String activeGameId
    ) {
    }

    /**
     * 세트 하나.
     *
     * state 는 esports-api 기준이라 실제 종료보다 약 5분 늦다 (2026-08-13 실측:
     * 피드 finished 17:39:34 → state=completed 17:44:43). 그동안 화면이 멈춘 것처럼
     * 보이므로, 피드가 먼저 알려주는 종료 여부를 feedFinished 로 함께 내린다.
     *
     * @param feedFinished livestats 가 이 세트를 finished 로 준 상태 (즉시 갱신)
     * @param winnerTeamId 세트 승리 팀 id — gameWins 증가분으로 판정하므로 state=completed
     *                     이후에만 채워진다 (약 5분 지연). 미확정이면 null
     */
    public record GameItem(
            String gameId,
            Integer number,
            String state,
            boolean feedFinished,
            String winnerTeamId,
            /**
             * 소스가 이 세트의 인게임 통계를 제공하지 않는다.
             * getLive 는 전 세계 리그를 주는데 일부 리그는 livestats 가 없다.
             */
            boolean statsUnavailable
    ) {
    }

    // ---- /api/live/{gameId}/scoreboard ----

    public record Scoreboard(
            String gameId,
            String rfc460Timestamp,
            String gameState,
            String patchVersion,
            // 피드에 게임 시계 필드가 없어 첫 프레임 기준으로 계산한 값 (시작 시각 미확정 시 null)
            Long gameTimeSeconds,
            Long goldDiff,       // blue - red
            TeamScoreboard blue,
            TeamScoreboard red
    ) {
    }

    public record TeamScoreboard(
            String esportsTeamId,
            Long totalGold,
            Integer totalKills,
            Integer towers,
            Integer inhibitors,
            Integer barons,
            List<String> dragons,
            List<PlayerRow> participants
    ) {
    }

    public record PlayerRow(
            Integer participantId,
            String summonerName,
            String championId,
            String role,
            Integer level,
            Integer kills,
            Integer deaths,
            Integer assists,
            Integer creepScore,
            Long totalGold,
            Integer currentHealth,
            Integer maxHealth
    ) {
    }

    // ---- /api/live/{gameId}/history ----

    /** 골드차 추이 한 점 */
    public record HistoryPoint(
            Long gameTimeSeconds,  // 게임 시작 기준 경과 초
            Long goldDiff,         // blue - red
            Long blueGold,
            Long redGold,
            Integer blueKills,
            Integer redKills
    ) {
    }

    /**
     * 오브젝트 획득 이벤트.
     * 피드에 이벤트 타임스탬프가 없어, 프레임 간 개수 증가를 감지해 시점을 역산한다
     * (프레임 간격만큼의 오차가 있다).
     */
    public record ObjectiveEvent(
            Long gameTimeSeconds,
            String side,      // blue | red
            String type,      // dragon | baron | tower | inhibitor
            String subtype    // 용 종류 (chemtech/ocean/...) — 그 외는 null
    ) {
    }

    public record GameHistory(
            String gameId,
            List<HistoryPoint> points,
            List<ObjectiveEvent> objectives
    ) {
    }

    // ---- /api/live/{gameId}/details ----

    public record GameDetails(
            String gameId,
            String rfc460Timestamp,
            List<PlayerDetail> participants
    ) {
    }

    public record PlayerDetail(
            Integer participantId,
            String summonerName,   // window 메타데이터에서 보강 (없으면 null)
            String championId,
            Double killParticipation,
            Double championDamageShare,
            Integer wardsPlaced,
            Integer wardsDestroyed,
            Long totalGoldEarned,
            List<Long> items,      // 아이템 ID 그대로 노출. TODO: Data Dragon 매핑으로 이름/아이콘 변환 (다음 단계)
            List<Long> perks       // perkMetadata.perks 그대로. TODO: 룬 이름 매핑 (다음 단계)
    ) {
    }
}
