package com.clutch.watch.redis;

import java.util.Map;

/**
 * Redis에 보존하는 시청 세션의 최종 정산용 상태.
 */
public record WatchSessionSnapshot(
        long userId,
        long matchId,
        String sessionKey,
        long enteredAt,
        long lastSeen,
        long eligibleMilliseconds,
        long sequence
) {

    /**
     * Redis Hash 조회 결과를 시청 세션 snapshot으로 변환한다.
     *
     * @param sessionKey 시청 세션 외부 식별자
     * @param fields Redis session Hash의 필드와 값
     * @return 변환된 시청 세션 snapshot
     * @throws IllegalStateException 필수 필드가 존재하지 않는 경우
     * @throws NumberFormatException 숫자 필드가 long으로 변환될 수 없는 경우
     */
    static WatchSessionSnapshot from(String sessionKey, Map<Object, Object> fields) {
        return new WatchSessionSnapshot(
                getRequiredLong(fields, "userId"),
                getRequiredLong(fields, "matchId"),
                sessionKey,
                getRequiredLong(fields, "enteredAt"),
                getRequiredLong(fields, "lastSeen"),
                getRequiredLong(fields, "eligibleMilliseconds"),
                getRequiredLong(fields, "sequence")
        );
    }

    /**
     * 필수 Redis Hash 필드를 조회하고 long 값으로 변환한다.
     *
     * @param fields Redis session Hash의 필드와 값
     * @param field 조회할 필드 이름
     * @return 필드 값을 변환한 long 값
     * @throws IllegalStateException 필드가 존재하지 않는 경우
     * @throws NumberFormatException 필드 값이 long으로 변환될 수 없는 경우
     */
    private static long getRequiredLong(Map<Object, Object> fields, String field) {
        Object value = fields.get(field);
        if (value == null) {
            throw new IllegalStateException("Redis 시청 세션에 " + field + " 필드가 없습니다.");
        }
        return Long.parseLong(value.toString());
    }
}
