package com.clutch.watch.redis.session;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 클래스패스의 시청 세션 Lua 스크립트를 Redis 실행 객체로 제공한다.
 */
final class WatchRedisScripts {

    private static final String SCRIPT_DIRECTORY = "redis/watch/";

    static final DefaultRedisScript<String> PREPARE_REWARD_CLAIM = load(
            "prepare-reward-claim.lua",
            String.class
    );
    static final DefaultRedisScript<String> COMPLETE_REWARD_CLAIM = load(
            "complete-reward-claim.lua",
            String.class
    );
    static final DefaultRedisScript<String> REPLACE_SESSION_KEY = load(
            "replace-session-key.lua",
            String.class
    );
    static final DefaultRedisScript<String> HEARTBEAT = load(
            "heartbeat.lua",
            String.class
    );
    static final DefaultRedisScript<Long> COMPARE_AND_DELETE = load(
            "compare-and-delete.lua",
            Long.class
    );

    private WatchRedisScripts() {
    }

    private static <T> DefaultRedisScript<T> load(String fileName, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(SCRIPT_DIRECTORY + fileName));
        script.setResultType(resultType);
        return script;
    }
}
