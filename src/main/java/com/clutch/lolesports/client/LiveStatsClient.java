package com.clutch.lolesports.client;

import com.clutch.lolesports.config.LolesportsProperties;
import com.clutch.lolesports.dto.external.DetailsResponse;
import com.clutch.lolesports.dto.external.WindowResponse;
import com.clutch.lolesports.source.ExternalSourceRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * feed.lolesports.com/livestats/v1 호출 클라이언트.
 *
 * startingTime 동작 (2026-08-07 / 08-08 라이브·종료 게임 실검증):
 *  - 10초 단위로 떨어지는 RFC3339 시각이어야 함
 *  - 게임 종료 이후 시각을 주면 마지막 프레임(gameState=finished)으로 클램핑됨
 *  - 생략하면 최신이 아니라 게임 시작 첫 프레임이 온다 (주의)
 *
 * 최소 지연은 고정값이 아니다 — 서버가 방송 지연에 맞춰 경기마다 다르게 강제한다.
 * (2026-08-07 경기: 20초 / 2026-08-08 경기: 40초)
 * 요구치보다 최신을 요청하면 400 + 아래 형태의 본문이 온다:
 *   "disallowed window with end time less than 40 sec old (was 17.62 sec old) ... ahead of broadcast"
 * 이 값을 파싱해 lag 을 자동으로 올려, 매 경기 그 경기의 최소 지연에 붙는다.
 */
@Component
public class LiveStatsClient {

    private static final Logger log = LoggerFactory.getLogger(LiveStatsClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** 400 본문에서 서버가 요구하는 최소 지연(초)을 추출 */
    private static final Pattern REQUIRED_AGE =
            Pattern.compile("less than (\\d+(?:\\.\\d+)?) sec old");

    /** 요청 창은 startingTime + 10초이므로, "창 끝"이 요구치를 넘으려면 여기에 10초를 더해야 한다 */
    private static final long WINDOW_SPAN_SECONDS = 10;
    /** 시계 오차·왕복 지연 흡수용 여유 */
    private static final long SAFETY_MARGIN_SECONDS = 5;
    private static final long MAX_LAG_SECONDS = 300;

    private final ExternalSourceRouter sourceRouter;
    private final LolesportsProperties props;

    /** 서버 요구에 맞춰 자동 조정되는 현재 lag(초). 초기값은 설정값 */
    private final AtomicLong effectiveLag;

    public LiveStatsClient(ExternalSourceRouter sourceRouter, LolesportsProperties props) {
        this.sourceRouter = sourceRouter;
        this.props = props;
        this.effectiveLag = new AtomicLong(props.liveStatsLagSeconds());
    }

    /** 팀 단위 스탯. 게임 시작 전이면 204/404 → null 반환 */
    public WindowResponse getWindow(String gameId) {
        try {
            return sourceRouter.liveStatsClient().get()
                    .uri(uri -> uri.path("/window/{gameId}")
                            .queryParam("startingTime", startingTime())
                            .build(gameId))
                    .retrieve()
                    .bodyToMono(WindowResponse.class)
                    .block(TIMEOUT);
        } catch (WebClientResponseException.BadRequest e) {
            // lag 을 올려두면 다음 폴링(1초 뒤)이 성공한다
            if (adaptLag(e)) {
                return null;
            }
            throw e;
        }
    }

    /** 특정 시점의 window (과거 경기 타임라인 수집용) */
    public WindowResponse getWindowAt(String gameId, Instant at) {
        long epoch = at.getEpochSecond();
        epoch -= epoch % 10;
        String ts = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(epoch));
        return sourceRouter.liveStatsClient().get()
                .uri(uri -> uri.path("/window/{gameId}")
                        .queryParam("startingTime", ts)
                        .build(gameId))
                .retrieve()
                .bodyToMono(WindowResponse.class)
                .block(TIMEOUT);
    }

    /** 선수 상세 스탯. 게임 시작 전이면 204/404 → null 반환 */
    public DetailsResponse getDetails(String gameId) {
        try {
            return sourceRouter.liveStatsClient().get()
                    .uri(uri -> uri.path("/details/{gameId}")
                            .queryParam("startingTime", startingTime())
                            .build(gameId))
                    .retrieve()
                    .bodyToMono(DetailsResponse.class)
                    .block(TIMEOUT);
        } catch (WebClientResponseException.BadRequest e) {
            if (adaptLag(e)) {
                return null;
            }
            throw e;
        }
    }

    /** paused 구간을 훑을 최대 횟수 (10초 창 × 30 = 5분). 로딩이 이보다 길지는 않다 */
    private static final int GAME_START_MAX_WINDOWS = 30;

    /**
     * 게임 경과 시간의 기준점 — 실제 게임이 시작된 프레임 시각.
     *
     * 피드에 게임 시계 필드가 없어 "프레임 시각 - 이 값"으로 경과 시간을 계산한다.
     * 첫 프레임을 그대로 쓰면 안 된다. 로딩·대기 구간이 gameState=paused 로 먼저 오고
     * (2026-08-19 GEN vs KT 실측: paused 가 약 90초), 그 시각을 기준으로 삼으면 화면
     * 시계가 인게임보다 그만큼 빨라진다.
     *
     * 그래서 paused 가 아닌 첫 프레임을 찾을 때까지 10초 창을 앞으로 넘긴다.
     * 게임당 1회만 호출되므로 폴링 부하에는 영향이 없다.
     *
     * @return in_game 첫 프레임의 rfc460Timestamp, 없으면 null
     */
    public String getGameStartTimestamp(String gameId) {
        try {
            WindowResponse res = sourceRouter.liveStatsClient().get()
                    .uri(uri -> uri.path("/window/{gameId}").build(gameId))
                    .retrieve()
                    .bodyToMono(WindowResponse.class)
                    .block(TIMEOUT);
            if (res == null || res.frames() == null || res.frames().isEmpty()) {
                return null;
            }
            String started = firstStartedFrame(res);
            if (started != null) {
                return started;
            }

            // 첫 창이 통째로 paused — 10초씩 넘기며 게임이 시작된 프레임을 찾는다
            Instant cursor = Instant.parse(
                    res.frames().get(res.frames().size() - 1).rfc460Timestamp());
            for (int i = 0; i < GAME_START_MAX_WINDOWS; i++) {
                cursor = cursor.plusSeconds(10);
                WindowResponse next = getWindowAt(gameId, cursor);
                if (next == null || next.frames() == null || next.frames().isEmpty()) {
                    break;
                }
                started = firstStartedFrame(next);
                if (started != null) {
                    return started;
                }
                cursor = Instant.parse(
                        next.frames().get(next.frames().size() - 1).rfc460Timestamp());
            }
            // 끝내 못 찾으면 첫 프레임으로 폴백한다 (시계가 빠를 수는 있어도 화면은 뜬다)
            log.warn("게임 {} 시작 프레임을 찾지 못해 첫 프레임을 기준으로 쓴다", gameId);
            return res.frames().get(0).rfc460Timestamp();
        } catch (Exception e) {
            log.warn("게임 시작 시각 조회 실패 (gameId={}): {}", gameId, e.toString());
            return null;
        }
    }

    /** 이 응답에서 paused 가 아닌 첫 프레임 시각 — 없으면 null */
    private static String firstStartedFrame(WindowResponse res) {
        for (WindowResponse.Frame f : res.frames()) {
            if (f.rfc460Timestamp() != null && !"paused".equalsIgnoreCase(f.gameState())) {
                return f.rfc460Timestamp();
            }
        }
        return null;
    }

    /**
     * 400 본문의 요구치를 읽어 lag 을 올린다.
     *
     * @return 이 400 이 "너무 최신 요청" 때문이라 처리했으면 true (호출부는 에러 대신 데이터 없음으로 취급)
     */
    private boolean adaptLag(WebClientResponseException e) {
        String body = e.getResponseBodyAsString();
        Matcher m = REQUIRED_AGE.matcher(body);
        if (!m.find()) {
            return false; // 다른 이유의 400 — 호출부에서 에러로 처리
        }

        long required = (long) Math.ceil(Double.parseDouble(m.group(1)));
        long newLag = Math.min(MAX_LAG_SECONDS, required + WINDOW_SPAN_SECONDS + SAFETY_MARGIN_SECONDS);

        long prev = effectiveLag.get();
        if (newLag > prev) {
            effectiveLag.set(newLag);
            log.info("livestats 최소 지연 상향: {}초 → {}초 (서버 요구 {}초)", prev, newLag, required);
        }
        return true;
    }

    /** 현재 적용 중인 lag(초) — /api/debug 노출용 */
    public long currentLagSeconds() {
        return effectiveLag.get();
    }

    /** now - lag초를 10초 단위로 내림한 RFC3339 문자열 */
    private String startingTime() {
        long epoch = Instant.now().getEpochSecond() - effectiveLag.get();
        epoch -= epoch % 10;
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(epoch));
    }
}
