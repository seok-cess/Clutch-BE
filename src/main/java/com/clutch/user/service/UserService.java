package com.clutch.user.service;

import com.clutch.betting.domain.BetPointTransactionType;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.common.privacy.PersonalDataMasker;
import com.clutch.user.domain.User;
import com.clutch.user.domain.UserRole;
import com.clutch.user.dto.PointRanking;
import com.clutch.user.dto.UserPointSummary;
import com.clutch.user.exception.UserNotFoundException;
import com.clutch.user.repository.UserRepository;
import com.clutch.watch.repository.WatchPointTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
