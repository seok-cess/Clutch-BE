package com.clutch.watch.redis.session;

import com.clutch.watch.redis.heartbeat.HeartbeatResult;
import com.clutch.watch.config.WatchRewardProperties;
import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchRedisErrorTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    /**
     * Redis session Hash 필수 필드가 없으면 데이터 손상 오류로 변환하는지 검증한다.
     */
    @Test
    void rejectsSessionHashWithMissingField() {
        Map<Object, Object> fields = validFields();
        fields.remove("matchId");

        assertWatchError(
                () -> WatchSessionSnapshot.from("session-key", fields),
                WatchError.REDIS_SESSION_FIELD_MISSING
        );
    }

    /**
     * Redis session Hash 숫자 필드가 long 형식이 아니면 데이터 형식 오류로 변환하는지 검증한다.
     */
    @Test
    void rejectsSessionHashWithInvalidNumber() {
        Map<Object, Object> fields = validFields();
        fields.put("eligibleMilliseconds", "not-a-number");

        assertThatThrownBy(() -> WatchSessionSnapshot.from("session-key", fields))
                .isInstanceOfSatisfying(WatchException.class, exception -> {
                    assertThat(exception.getError()).isEqualTo(WatchError.REDIS_SESSION_FIELD_INVALID);
                    assertThat(exception.getCause()).isInstanceOf(NumberFormatException.class);
                });
    }

    /**
     * Lua script가 정의하지 않은 문자열을 반환하면 알 수 없는 결과 오류로 변환하는지 검증한다.
     */
    @Test
    void rejectsUnknownHeartbeatResult() {
        assertWatchError(() -> HeartbeatResult.from("UNKNOWN"), WatchError.HEARTBEAT_RESULT_UNKNOWN);
    }

    /**
     * RedisTemplate이 Lua 실행 결과를 반환하지 않으면 명시적인 결과 누락 오류를 발생시키는지 검증한다.
     */
    @Test
    @SuppressWarnings("unchecked")
    void rejectsMissingHeartbeatScriptResult() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(null);
        WatchSessionRedisRepository repository = new WatchSessionRedisRepository(redisTemplate, properties());

        assertWatchError(
                () -> repository.heartbeat(100L, "session-key", 1L, 1_000L),
                WatchError.HEARTBEAT_RESULT_MISSING
        );
    }

    /**
     * 정상적인 Redis session Hash 필드 집합을 생성한다.
     *
     * @return 모든 필수 숫자 필드를 가진 Hash
     */
    private Map<Object, Object> validFields() {
        Map<Object, Object> fields = new HashMap<>();
        fields.put("userId", "100");
        fields.put("matchId", "200");
        fields.put("enteredAt", "1000");
        fields.put("lastSeen", "31000");
        fields.put("eligibleMilliseconds", "30000");
        fields.put("sequence", "1");
        fields.put("rewardSequence", "1");
        return fields;
    }

    /**
     * Redis 오류 테스트에 사용할 시청 보상 정책을 생성한다.
     *
     * @return 기본 TTL 정책
     */
    private WatchRewardProperties properties() {
        return new WatchRewardProperties(
                Duration.ofSeconds(30), Duration.ofSeconds(90), Duration.ofSeconds(120),
                Duration.ofHours(1), Duration.ofSeconds(10), Duration.ofSeconds(60),
                Duration.ofMinutes(5), 100L
        );
    }

    /**
     * 실행 결과가 기대한 Watch 오류인지 검증한다.
     *
     * @param runnable 예외가 발생해야 하는 실행 코드
     * @param expectedError 기대하는 Watch 오류
     */
    private void assertWatchError(Runnable runnable, WatchError expectedError) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(WatchException.class,
                        exception -> assertThat(exception.getError()).isEqualTo(expectedError));
    }
}
