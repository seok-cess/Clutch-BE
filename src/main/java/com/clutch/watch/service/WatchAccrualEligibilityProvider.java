package com.clutch.watch.service;

/**
 * 현재 경기에서 시청 시간을 적립할 수 있는지 제공한다.
 */
public interface WatchAccrualEligibilityProvider {

    /**
     * 진행 중인 세트가 존재하는 동안에만 시청 시간 적립을 허용한다.
     *
     * @param matchId 내부 경기 ID
     * @return 현재 시청 시간을 적립할 수 있으면 true
     */
    boolean canAccumulate(long matchId);
}
