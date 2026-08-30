package com.clutch.user.service;

import com.clutch.betting.domain.BetPointTransactionType;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.common.privacy.PersonalDataMasker;
import com.clutch.user.domain.User;
import com.clutch.user.domain.UserRole;
import com.clutch.user.dto.MyPointRanking;
import com.clutch.user.dto.PointRanking;
import com.clutch.user.dto.PointTransactionHistory;
import com.clutch.user.dto.PointTransactionType;
import com.clutch.user.dto.UserPointSummary;
import com.clutch.user.exception.UserNotFoundException;
import com.clutch.user.repository.UserRepository;
import com.clutch.watch.repository.WatchPointTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** 사용자 포인트 정보와 포인트 순위를 조회한다. */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final int POINT_RANKING_LIMIT = 10;

    private final UserRepository userRepository;
    private final UserBetRepository userBetRepository;
    private final BetPointTransactionRepository betPointTransactionRepository;
    private final WatchPointTransactionRepository watchPointTransactionRepository;
    private final PersonalDataMasker personalDataMasker;

    /**
     * 사용자의 현재 보유 포인트를 조회한다.
     *
     * @param userId 사용자 ID
     * @return 조회 시점의 보유 포인트
     * @throws UserNotFoundException 사용자를 찾을 수 없을 때
     */
    @Transactional(readOnly = true)
    public long getPoint(Long userId) {
        return userRepository.findPointById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    /**
     * 포인트 모달의 개인 정보 탭에 필요한 포인트와 승부예측 요약을 조회한다.
     * 환불은 사용자가 새로 얻은 포인트가 아니므로 최고 획득 포인트에서 제외한다.
     *
     * @param userId 사용자 ID
     * @return 보유 포인트, 예측 횟수, 적중 횟수와 단일 최대 획득 포인트
     */
    @Transactional(readOnly = true)
    public UserPointSummary getPointSummary(Long userId) {
        long point = getPoint(userId);
        long predictionCount = userBetRepository.countByUserId(userId);
        long predictionSuccessCount = userBetRepository.countByUserIdAndStatus(
                userId,
                UserBetStatus.WON
        );
        long maxBetPayout = betPointTransactionRepository
                .findMaxPointDeltaByUserIdAndTransactionType(
                        userId,
                        BetPointTransactionType.PAYOUT
                );
        long maxWatchAward = watchPointTransactionRepository
                .findMaxAwardedPointByUserId(userId);

        return new UserPointSummary(
                point,
                predictionCount,
                predictionSuccessCount,
                Math.max(maxBetPayout, maxWatchAward)
        );
    }

    /**
     * 현재 사용자의 전체 보유 포인트 순위를 조회한다.
     * 동점자는 같은 순위를 가지며, 나보다 포인트가 높은 일반 사용자 수에 1을 더해 계산한다.
     *
     * @param userId 사용자 ID
     * @return 현재 보유 포인트와 전체 사용자 중 순위
     */
    @Transactional(readOnly = true)
    public MyPointRanking getMyPointRanking(Long userId) {
        long point = getPoint(userId);
        long rank = userRepository.countByRoleAndPointGreaterThan(UserRole.USER, point) + 1L;
        return new MyPointRanking(point, rank);
    }

    /**
     * 시청 수령과 승부예측으로 발생한 포인트 증감 원장을 최신 순으로 조회한다.
     *
     * @param userId 사용자 ID
     * @return 시청 보상·배팅 차감·적중 지급·환불이 합쳐진 포인트 내역
     * @throws UserNotFoundException 사용자를 찾을 수 없을 때
     */
    @Transactional(readOnly = true)
    public List<PointTransactionHistory> getPointTransactionHistory(Long userId) {
        getPoint(userId);

        List<PointTransactionHistory> watchTransactions = watchPointTransactionRepository
                .findAllByUserIdOrderByCreatedAtDescIdDesc(userId)
                .stream()
                .map(transaction -> new PointTransactionHistory(
                        "watch-" + transaction.getId(),
                        PointTransactionType.WATCH_REWARD,
                        transaction.getAwardedPoint(),
                        transaction.getCreatedAt()
                ))
                .toList();

        List<Long> userBetIds = userBetRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId)
                .stream()
                .map(userBet -> userBet.getId())
                .toList();
        if (userBetIds.isEmpty()) {
            return watchTransactions;
        }

        List<PointTransactionHistory> betTransactions = betPointTransactionRepository
                .findAllByUserBetIdIn(userBetIds)
                .stream()
                .map(transaction -> new PointTransactionHistory(
                        "bet-" + transaction.getId(),
                        toPointTransactionType(transaction.getTransactionType()),
                        transaction.getPointDelta(),
                        transaction.getCreatedAt()
                ))
                .toList();

        return Stream.concat(watchTransactions.stream(), betTransactions.stream())
                .sorted(Comparator.comparing(PointTransactionHistory::createdAt).reversed()
                        .thenComparing(PointTransactionHistory::transactionId))
                .toList();
    }

    /**
     * 보유 포인트 기준 전체 사용자 상위 10명을 조회한다. 동점은 사용자 ID 오름차순으로 정렬한다.
     * 공개 순위에는 개인정보 보호를 위해 사용자 이름을 마스킹해 반환한다.
     *
     * @return 보유 포인트 상위 10명
     */
    @Transactional(readOnly = true)
    public List<PointRanking> getPointRankings() {
        List<User> users = userRepository.findAllByRoleOrderByPointDescIdAsc(
                UserRole.USER,
                PageRequest.of(0, POINT_RANKING_LIMIT)
        );

        return java.util.stream.IntStream.range(0, users.size())
                .mapToObj(index -> toPointRanking(index + 1, users.get(index)))
                .toList();
    }

    private PointRanking toPointRanking(int rank, User user) {
        String displayName = personalDataMasker.maskName(user.getName());
        if (displayName == null) {
            displayName = "익명 사용자";
        }
        return new PointRanking(rank, displayName, user.getPoint());
    }

    private PointTransactionType toPointTransactionType(BetPointTransactionType transactionType) {
        return switch (transactionType) {
            case STAKE -> PointTransactionType.BET_STAKE;
            case PAYOUT -> PointTransactionType.BET_PAYOUT;
            case REFUND -> PointTransactionType.BET_REFUND;
        };
    }
}
