package com.clutch.watch.redis.reward;

/**
 * Redis 포인트 수령의 자격 검증 및 회차 전환 상태.
 */
public enum RewardClaimStatus {
    SUCCESS,
    ALREADY_COMPLETED,
    REPLACED,
    EXPIRED,
    SESSION_NOT_FOUND,
    USER_MISMATCH,
    INVALID_REWARD_SEQUENCE,
    NOT_CLAIMABLE
}
