package com.clutch.coupon.claim.recovery;

import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.redis.CouponClaimRedisKeys;
import com.clutch.coupon.claim.redis.CouponStockInitializer;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.contract.issuance.CouponIssuanceRecoveryReader;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.domain.CouponEventOccurrence;
import com.clutch.coupon.event.domain.CouponEventOccurrenceStatus;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.event.repository.CouponEventOccurrenceRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_REDIS_UNAVAILABLE;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_STOCK_INCONSISTENT;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_STOCK_RECOVERING;
import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_STOCK_RECOVERY_FAILED;

/** MySQL 기준 쿠폰 Redis 재구축 */
@Service
public class CouponStockRecoveryService {

    private final CouponStockRecoveryStateManager stateManager;
    private final CouponEventOccurrenceRepository occurrenceRepository;
    private final CouponEventItemRepository itemRepository;
    private final CouponClaimRequestRepository claimRequestRepository;
    private final CouponIssuanceRecoveryReader issuanceRecoveryReader;
    private final StringRedisTemplate stringRedisTemplate;
    private final CouponStockInitializer couponStockInitializer;

    private final RedisScript<Long> recoveryScript;

    public CouponStockRecoveryService(
            CouponStockRecoveryStateManager stateManager,
            CouponEventOccurrenceRepository occurrenceRepository,
            CouponEventItemRepository itemRepository,
            CouponClaimRequestRepository claimRequestRepository,
            CouponIssuanceRecoveryReader issuanceRecoveryReader,
            StringRedisTemplate stringRedisTemplate,
            CouponStockInitializer couponStockInitializer,
            @Qualifier("couponStockRecoveryScript")
            RedisScript<Long> recoveryScript
    ) {
        this.stateManager = stateManager;
        this.occurrenceRepository = occurrenceRepository;
        this.itemRepository = itemRepository;
        this.claimRequestRepository = claimRequestRepository;
        this.issuanceRecoveryReader = issuanceRecoveryReader;
        this.stringRedisTemplate = stringRedisTemplate;
        this.couponStockInitializer = couponStockInitializer;
        this.recoveryScript = recoveryScript;
    }

    /** 열린 쿠폰 회차 Redis 재구축 */
    @Transactional(readOnly = true)
    public synchronized CouponStockRecoveryResult recoverOpenOccurrences() {
        if (!stateManager.beginRecovery()) {
            throw new CouponClaimException(COUPON_STOCK_RECOVERING);
        }

        try {
            List<CouponEventOccurrence> occurrences =
                    occurrenceRepository.findAllByOccurrenceStatus(
                            CouponEventOccurrenceStatus.OPEN
                    );

            int recoveredItems = 0;
            int recoveredUsers = 0;

            for (CouponEventOccurrence occurrence : occurrences) {
                RecoverySnapshot snapshot = createSnapshot(occurrence);
                rebuildRedis(snapshot);
                verifyRedis(snapshot);
                couponStockInitializer.initialize(
                        occurrence.getCouponEventId(),
                        occurrence.getId(),
                        occurrence.getOpenedAt(),
                        occurrence.getExpiresAt()
                );
                recoveredItems += snapshot.items().size();
                recoveredUsers += snapshot.userIds().size();
            }

            stateManager.markReady();
            return new CouponStockRecoveryResult(
                    CouponStockRecoveryState.READY,
                    occurrences.size(),
                    recoveredItems,
                    recoveredUsers
            );
        } catch (CouponClaimException exception) {
            stateManager.markFailed();
            throw exception;
        } catch (DataAccessException exception) {
            stateManager.markUnavailable();
            throw new CouponClaimException(
                    COUPON_REDIS_UNAVAILABLE,
                    exception
            );
        } catch (RuntimeException exception) {
            stateManager.markFailed();
            throw new CouponClaimException(
                    COUPON_STOCK_RECOVERY_FAILED,
                    exception
            );
        }
    }

    private RecoverySnapshot createSnapshot(
            CouponEventOccurrence occurrence
    ) {
        List<CouponEventItem> items = itemRepository
                .findAllByCouponEventId(occurrence.getCouponEventId());

        List<RecoveryItem> recoveryItems = new ArrayList<>();

        for (CouponEventItem item : items) {
            long succeededRequests = claimRequestRepository
                    .countByCouponEventItemIdAndRequestStatus(
                            item.getId(),
                            ClaimRequestStatus.SUCCEEDED
                    );
            long issuedCoupons = issuanceRecoveryReader
                    .countIssuedCoupons(item.getId());

            if (succeededRequests != issuedCoupons
                    || issuedCoupons > item.getQuantity()) {
                throw new CouponClaimException(
                        COUPON_STOCK_INCONSISTENT
                );
            }

            recoveryItems.add(
                    new RecoveryItem(
                            item.getId(),
                            item.getQuantity() - issuedCoupons
                    )
            );
        }

        List<Long> succeededUserIds = claimRequestRepository
                .findUserIdsByOccurrenceIdAndStatus(
                        occurrence.getId(),
                        ClaimRequestStatus.SUCCEEDED
                );
        List<Long> issuedUserIds = issuanceRecoveryReader
                .findIssuedUserIds(occurrence.getId());

        if (!sameUsers(succeededUserIds, issuedUserIds)) {
            throw new CouponClaimException(COUPON_STOCK_INCONSISTENT);
        }

        return new RecoverySnapshot(
                occurrence.getId(),
                recoveryItems,
                issuedUserIds
        );
    }

    private boolean sameUsers(List<Long> first, List<Long> second) {
        Set<Long> firstUsers = new HashSet<>(first);
        Set<Long> secondUsers = new HashSet<>(second);
        return first.size() == firstUsers.size()
                && second.size() == secondUsers.size()
                && firstUsers.equals(secondUsers);
    }

    private void rebuildRedis(RecoverySnapshot snapshot) {
        List<String> keys = new ArrayList<>();
        keys.add(CouponClaimRedisKeys.claimedUsers(snapshot.occurrenceId()));
        snapshot.items().forEach(item ->
                keys.add(CouponClaimRedisKeys.stock(item.itemId()))
        );

        List<String> arguments = new ArrayList<>();
        arguments.add(String.valueOf(snapshot.userIds().size()));
        snapshot.userIds().forEach(userId ->
                arguments.add(String.valueOf(userId))
        );
        snapshot.items().forEach(item ->
                arguments.add(String.valueOf(item.remainingStock()))
        );

        Long result = stringRedisTemplate.execute(
                recoveryScript,
                keys,
                arguments.toArray()
        );
        if (result == null || result != keys.size()) {
            throw new CouponClaimException(
                    COUPON_STOCK_RECOVERY_FAILED
            );
        }
    }

    private void verifyRedis(RecoverySnapshot snapshot) {
        for (RecoveryItem item : snapshot.items()) {
            String stock = stringRedisTemplate.opsForValue().get(
                    CouponClaimRedisKeys.stock(item.itemId())
            );
            if (!String.valueOf(item.remainingStock()).equals(stock)) {
                throw new CouponClaimException(
                        COUPON_STOCK_RECOVERY_FAILED
                );
            }
        }

        Long claimedCount = stringRedisTemplate.opsForSet().size(
                CouponClaimRedisKeys.claimedUsers(snapshot.occurrenceId())
        );
        if (claimedCount == null
                || claimedCount != snapshot.userIds().size()) {
            throw new CouponClaimException(
                    COUPON_STOCK_RECOVERY_FAILED
            );
        }
    }

    private record RecoverySnapshot(
            Long occurrenceId,
            List<RecoveryItem> items,
            List<Long> userIds
    ) {
    }

    private record RecoveryItem(
            Long itemId,
            long remainingStock
    ) {
    }
}
