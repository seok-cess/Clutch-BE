package com.clutch.watch.redis.reward;

import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;

/**
 * Redis 수령 회차 전환 상태와 다음 수령 회차.
 */
public record RewardClaimCompletionResult(
        RewardClaimCompletionStatus status,
        long nextRewardSequence
) {

    public static RewardClaimCompletionResult from(String value) {
        String[] fields = value.split(":", -1);
        try {
            RewardClaimCompletionStatus status = RewardClaimCompletionStatus.valueOf(fields[0]);
            if (status == RewardClaimCompletionStatus.SUCCESS
                    || status == RewardClaimCompletionStatus.ALREADY_COMPLETED) {
                if (fields.length != 2) {
                    throw new WatchException(WatchError.REWARD_CLAIM_RESULT_UNKNOWN);
                }
                return new RewardClaimCompletionResult(status, Long.parseLong(fields[1]));
            }
            if (fields.length != 1) {
                throw new WatchException(WatchError.REWARD_CLAIM_RESULT_UNKNOWN);
            }
            return new RewardClaimCompletionResult(status, 0L);
        } catch (IllegalArgumentException exception) {
            throw new WatchException(WatchError.REWARD_CLAIM_RESULT_UNKNOWN, exception);
        }
    }
}
