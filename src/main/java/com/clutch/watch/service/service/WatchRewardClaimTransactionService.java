package com.clutch.watch.service.service;

import com.clutch.lolesports.entity.EsportsMatch;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import com.clutch.user.domain.User;
import com.clutch.user.repository.UserRepository;
import com.clutch.watch.domain.WatchPointTransaction;
import com.clutch.watch.domain.WatchSession;
import com.clutch.watch.domain.WatchSessionStatus;
import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;
import com.clutch.watch.repository.WatchPointTransactionRepository;
import com.clutch.watch.repository.WatchSessionRepository;
import com.clutch.watch.service.dto.WatchPointClaimTransactionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 포인트 변경과 시청 포인트 거래 저장의 DB 트랜잭션 경계.
 */
@Service
@RequiredArgsConstructor
public class WatchRewardClaimTransactionService {

    private static final String WATCHABLE_MATCH_STATUS = "inProgress";

    private final WatchSessionRepository watchSessionRepository;
    private final WatchPointTransactionRepository watchPointTransactionRepository;
    private final UserRepository userRepository;
    private final EsportsMatchRepository esportsMatchRepository;

    @Transactional
    public WatchPointClaimTransactionResult award(
            long userId,
            String sessionKey,
            long rewardSequence,
            long rewardPoint
    ) {
        WatchSession watchSession = watchSessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new WatchException(WatchError.WATCH_SESSION_NOT_FOUND));
        validateSession(watchSession, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new WatchException(WatchError.USER_NOT_FOUND));
        WatchPointTransaction existingTransaction = watchPointTransactionRepository
                .findByWatchSessionIdAndRewardSequence(watchSession.getId(), rewardSequence)
                .orElse(null);
        if (existingTransaction != null) {
            return new WatchPointClaimTransactionResult(
                    rewardSequence,
                    existingTransaction.getAwardedPoint(),
                    user.getPoint(),
                    false
            );
        }

        validateMatch(watchSession.getEsportsMatchId());

        try {
            user.changePoint(rewardPoint);
        } catch (ArithmeticException exception) {
            throw new WatchException(WatchError.USER_POINT_OVERFLOW, exception);
        }
        watchPointTransactionRepository.save(WatchPointTransaction.create(
                userId,
                watchSession.getId(),
                rewardSequence,
                watchSession.getEsportsMatchId(),
                rewardPoint
        ));

        return new WatchPointClaimTransactionResult(
                rewardSequence,
                rewardPoint,
                user.getPoint(),
                true
        );
    }

    @Transactional(readOnly = true)
    public WatchPointClaimTransactionResult findExisting(
            long userId,
            String sessionKey,
            long rewardSequence
    ) {
        WatchSession watchSession = watchSessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new WatchException(WatchError.WATCH_SESSION_NOT_FOUND));
        if (!watchSession.getUserId().equals(userId)) {
            throw new WatchException(WatchError.WATCH_SESSION_USER_MISMATCH);
        }
        WatchPointTransaction transaction = watchPointTransactionRepository
                .findByWatchSessionIdAndRewardSequence(watchSession.getId(), rewardSequence)
                .orElseThrow(() -> new WatchException(WatchError.POINT_TRANSACTION_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new WatchException(WatchError.USER_NOT_FOUND));
        return new WatchPointClaimTransactionResult(
                rewardSequence,
                transaction.getAwardedPoint(),
                user.getPoint(),
                false
        );
    }

    private void validateSession(WatchSession watchSession, long userId) {
        if (!watchSession.getUserId().equals(userId)) {
            throw new WatchException(WatchError.WATCH_SESSION_USER_MISMATCH);
        }
        if (watchSession.getStatus() != WatchSessionStatus.WATCHING) {
            throw new WatchException(WatchError.WATCH_SESSION_EXPIRED);
        }
    }

    private void validateMatch(long matchId) {
        EsportsMatch esportsMatch = esportsMatchRepository.findById(matchId)
                .orElseThrow(() -> new WatchException(WatchError.MATCH_NOT_FOUND));
        if (!WATCHABLE_MATCH_STATUS.equalsIgnoreCase(esportsMatch.getLifecycleStatus())) {
            throw new WatchException(WatchError.MATCH_NOT_WATCHABLE);
        }
    }
}
