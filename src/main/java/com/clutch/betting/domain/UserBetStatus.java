package com.clutch.betting.domain;

/** 사용자 배팅의 등록 및 최종 처리 결과 상태다. */
public enum UserBetStatus {
    PLACED,
    WON,
    LOST,
    REFUNDED
}
