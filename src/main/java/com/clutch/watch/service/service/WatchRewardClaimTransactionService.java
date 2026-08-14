package com.clutch.watch.service.service;

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

    private final WatchSessionRepository watchSessionRepository;
    private final WatchPointTransactionRepository watchPointTransactionRepository;
    private final UserRepository userRepository;

    /**
     * 사용자 포인트 증가와 회차별 시청 포인트 거래 저장을 하나의 DB 트랜잭션으로 처리한다.
     * 동일 세션과 회차의 거래가 이미 존재하면 포인트를 다시 지급하지 않고 기존 지급 결과를 반환한다.
     *
     * @param userId 포인트를 지급받을 사용자 ID
     * @param sessionKey 지급 대상 시청 세션 외부 식별자
     * @param rewardSequence 지급할 포인트 회차
     * @param rewardPoint 지급할 포인트
     * @return 확정된 회차, 지급 포인트와 사용자의 현재 총포인트
     * @throws WatchException 세션 또는 사용자가 없거나 세션이 지급 가능한 상태가 아닌 경우
     */
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
                    user.getPoint()
            );
        }

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
                user.getPoint()
        );
    }

    /**
     * 이미 지급된 세션 회차의 거래와 사용자의 현재 총포인트를 조회한다.
     * Redis 다음 회차 전환 후 동일 수령 요청이 재시도된 경우 멱등 응답을 복원하는 데 사용한다.
     *
     * @param userId 기존 지급 거래의 사용자 ID
     * @param sessionKey 기존 지급 거래의 시청 세션 외부 식별자
     * @param rewardSequence 조회할 포인트 회차
     * @return 기존 거래의 지급 포인트와 사용자의 현재 총포인트
     * @throws WatchException 세션, 거래 또는 사용자가 없거나 세션 소유자가 다른 경우
     */
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
                user.getPoint()
        );
    }

    /**
     * DB 시청 세션의 소유자와 포인트 지급 가능 상태를 검증한다.
     *
     * @param watchSession 검증할 DB 시청 세션
     * @param userId 포인트 수령을 요청한 사용자 ID
     * @throws WatchException 세션 소유자가 다르거나 세션이 이미 종료된 경우
     */
    private void validateSession(WatchSession watchSession, long userId) {
        if (!watchSession.getUserId().equals(userId)) {
            throw new WatchException(WatchError.WATCH_SESSION_USER_MISMATCH);
        }
        if (watchSession.getStatus() != WatchSessionStatus.WATCHING) {
            throw new WatchException(WatchError.WATCH_SESSION_EXPIRED);
        }
    }

}
