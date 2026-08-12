package com.clutch.watch.redis;

import com.clutch.watch.service.WatchRewardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis Alive TTL 만료를 감지하여 시청 세션의 포인트를 지급하고 Redis 상태를 정리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchAliveExpirationListener implements MessageListener {

    private static final String ALIVE_KEY_PREFIX = "watch:alive:";

    private final WatchSessionRedisRepository watchSessionRedisRepository;
    private final WatchRewardService watchRewardService;

    /**
     * Redis keyevent 채널에서 전달된 만료 키를 문자열로 변환하여 처리한다.
     *
     * @param message 만료된 Redis 키가 body에 담긴 메시지
     * @param pattern Listener 등록에 사용된 채널 패턴
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        handleExpiredKey(new String(message.getBody(), StandardCharsets.UTF_8));
    }

    /**
     * Alive 키 만료 시 session snapshot을 기준으로 포인트를 지급하고 Redis 상태를 정리한다.
     * Alive 키 이외의 만료 이벤트와 형식이 잘못된 키는 처리하지 않는다.
     *
     * @param expiredKey 만료된 Redis 키
     */
    void handleExpiredKey(String expiredKey) {
        ExpiredAliveKey aliveKey = parseAliveKey(expiredKey);
        if (aliveKey == null) {
            return;
        }

        watchSessionRedisRepository.findSession(aliveKey.sessionKey()).ifPresent(snapshot -> {
            watchRewardService.settle(snapshot);
            watchSessionRedisRepository.deleteActiveIfMatches(aliveKey.userId(), aliveKey.sessionKey());
            watchSessionRedisRepository.deleteSession(aliveKey.sessionKey());
        });
    }

    /**
     * {@code watch:alive:{userId}:{sessionKey}} 형식의 키에서 사용자와 세션 식별자를 추출한다.
     *
     * @param expiredKey 해석할 Redis 만료 키
     * @return 정상적인 Alive 키이면 추출 결과, 대상 키가 아니거나 형식이 잘못됐으면 null
     */
    private ExpiredAliveKey parseAliveKey(String expiredKey) {
        if (expiredKey == null || !expiredKey.startsWith(ALIVE_KEY_PREFIX)) {
            return null;
        }

        String identifiers = expiredKey.substring(ALIVE_KEY_PREFIX.length());
        int separatorIndex = identifiers.indexOf(':');
        if (separatorIndex <= 0 || separatorIndex == identifiers.length() - 1) {
            log.warn("형식이 잘못된 Redis Alive 만료 키를 무시합니다: {}", expiredKey);
            return null;
        }

        try {
            long userId = Long.parseLong(identifiers.substring(0, separatorIndex));
            String sessionKey = identifiers.substring(separatorIndex + 1);
            return new ExpiredAliveKey(userId, sessionKey);
        } catch (NumberFormatException exception) {
            log.warn("사용자 ID를 해석할 수 없는 Redis Alive 만료 키를 무시합니다: {}", expiredKey);
            return null;
        }
    }

    /**
     * Alive 만료 키에서 추출한 사용자 ID와 시청 세션 키.
     *
     * @param userId 시청 사용자 ID
     * @param sessionKey 만료된 시청 세션 외부 식별자
     */
    private record ExpiredAliveKey(long userId, String sessionKey) {
    }
}
