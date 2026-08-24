package com.clutch.wallet.domain;

/**
 * 지갑 아웃박스 레코드의 발행 상태.
 */
public enum WalletOutboxStatus {
    /** 아직 발행되지 않은 상태. */
    PENDING,
    /** 발행이 완료된 상태. */
    SENT
}
