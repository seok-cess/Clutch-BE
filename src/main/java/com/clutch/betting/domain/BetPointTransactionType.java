package com.clutch.betting.domain;

/** 배팅 포인트 원장의 차감·지급·환불 거래 유형이다. */
public enum BetPointTransactionType {
    /** 배팅 등록 시 포인트 차감 거래다. */
    STAKE,
    /** 배팅 적중 시 포인트 지급 거래다. */
    PAYOUT,
    /** 이벤트 취소 시 포인트 환불 거래다. */
    REFUND
}
