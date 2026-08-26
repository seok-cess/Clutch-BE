package com.clutch.coupon.claim.repository;

/** 다중 인스턴스에서 쿠폰 성공 수량 집계를 한 번만 실행하도록 직렬화한다. */
public interface CouponSuccessCountSynchronizationLock {

    /**
     * 잠금을 즉시 획득해 작업을 실행한다.
     *
     * @param task 잠금 안에서 실행할 집계 작업
     * @return 작업을 실행했으면 {@code true}, 다른 인스턴스가 실행 중이면 {@code false}
     */
    boolean tryExecute(Runnable task);
}
