package com.clutch.lolesports.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * lolesports.* 프로퍼티 바인딩.
 *
 * 설정이 없어도 동작하도록 모든 값에 기본값을 둔다.
 * (application.yaml 은 gitignore 대상이라 CI·신규 클론 환경에는 존재하지 않는다.
 *  기본값이 없으면 poll 이 null 이 되어 기동 시 NPE 가 난다.)
 * 값을 바꾸려면 application.yaml 이나 환경변수로 덮어쓰면 된다.
 */
@ConfigurationProperties(prefix = "lolesports")
public record LolesportsProperties(
        String apiKey,
        String esportsApiBaseUrl,
        String liveStatsBaseUrl,
        String locale,
        String leagueId,
        String tournamentId,
        /** livestats startingTime 을 현재보다 몇 초 과거로 잡을지 (하한 30초 — 창 끝이 20초 이상 과거여야 함) */
        long liveStatsLagSeconds,
        /** 화면 재생 시점 = now - 이 값(초). 프레임 버퍼에서 이 시점 프레임을 골라 응답 */
        long displayLagSeconds,
        /**
         * 매치 종료 판정 후 라이브 목록에 남겨둘 시간(초).
         *
         * 소스의 getLive 는 경기가 끝나도 언제 내려갈지 예측할 수 없다
         * (2026-08-14 OME vs DOG: 종료 4.8분 뒤에도 잔류). 그래서 소스를 기다리지 않고
         * 우리가 판정한 종료 시각 기준으로 이 시간이 지나면 목록에서 뺀다.
         * 사용자가 최종 결과를 확인할 여유를 주는 값이다.
         */
        long liveRetainAfterFinishSeconds,
        /**
         * livestats 를 제공하지 않는 리그명 목록.
         *
         * getLive 는 전 세계 리그를 주는데 일부 리그는 세트 내내 404 를 반환한다
         * (예: LRN — getLive 응답의 streams[].statsStatus 가 disabled).
         * 이런 리그는 물어봐야 소용이 없으므로 인게임 폴링 대상에서 아예 제외한다.
         * 리그명은 getSchedule/getLive 의 league.name 과 정확히 일치해야 한다.
         */
        List<String> statsDisabledLeagues,
        Poll poll
) {

    /** lolesports.com 프론트엔드에 공개된 공용 키 */
    private static final String DEFAULT_API_KEY = "0TvQnueqKa5mxJntVWt0w4LpLfEkrV1Ta8rQBb9Z";
    private static final String DEFAULT_ESPORTS_API = "https://esports-api.lolesports.com/persisted/gw";
    private static final String DEFAULT_LIVE_STATS = "https://feed.lolesports.com/livestats/v1";
    private static final String DEFAULT_LOCALE = "ko-KR";
    /** getLeagues 실호출로 확인한 LCK id */
    private static final String DEFAULT_LEAGUE_ID = "98767991310872058";
    /** getTournamentsForLeague 로 확인 — 시즌이 바뀌면 교체해야 한다 */
    private static final String DEFAULT_TOURNAMENT_ID = "115548147890329817";

    public LolesportsProperties {
        apiKey = orDefault(apiKey, DEFAULT_API_KEY);
        esportsApiBaseUrl = orDefault(esportsApiBaseUrl, DEFAULT_ESPORTS_API);
        liveStatsBaseUrl = orDefault(liveStatsBaseUrl, DEFAULT_LIVE_STATS);
        locale = orDefault(locale, DEFAULT_LOCALE);
        leagueId = orDefault(leagueId, DEFAULT_LEAGUE_ID);
        tournamentId = orDefault(tournamentId, DEFAULT_TOURNAMENT_ID);
        if (liveStatsLagSeconds <= 0) {
            liveStatsLagSeconds = 30;
        }
        if (displayLagSeconds <= 0) {
            displayLagSeconds = 15;
        }
        if (liveRetainAfterFinishSeconds <= 0) {
            liveRetainAfterFinishSeconds = 300;   // 5분
        }
        statsDisabledLeagues = statsDisabledLeagues == null
                ? List.of() : List.copyOf(statsDisabledLeagues);
        if (poll == null) {
            poll = new Poll(0, 0, 0, 0, 0);   // Poll 의 기본값 보정에 위임
        }
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    public record Poll(
            long liveCheckMs,
            long inGameMs,
            long metaMs,
            long backoffBaseMs,
            long backoffMaxMs
    ) {
        public Poll {
            if (liveCheckMs <= 0) {
                liveCheckMs = 60_000;    // getLive 감시 주기
            }
            if (inGameMs <= 0) {
                inGameMs = 1_000;        // window/details 인게임 폴링 주기
            }
            if (metaMs <= 0) {
                metaMs = 300_000;        // 일정·순위 갱신 (5분)
            }
            if (backoffBaseMs <= 0) {
                backoffBaseMs = 5_000;   // 연속 실패 시 백오프 시작 간격
            }
            if (backoffMaxMs <= 0) {
                backoffMaxMs = 300_000;  // 백오프 상한
            }
        }
    }
}
