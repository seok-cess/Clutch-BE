package com.clutch.watch.redis;

/**
 * DB 포인트 지급 후 Redis 회차 전환 결과.
 */
public enum RewardClaimCompletionStatus {
    SUCCESS,
    ALREADY_COMPLETED,
    REPLACED,
    EXPIRED,
    SESSION_NOT_FOUND,
    USER_MISMATCH,
    INVALID_REWARD_SEQUENCE,
    NOT_CLAIMABLE
}
