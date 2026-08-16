package com.clutch.betting.domain;

/** 배팅 이벤트의 오픈·종료·정산·취소 생명주기 상태다. */
public enum BettingEventStatus {
    /** 신규 배팅을 받을 수 있는 상태다. */
    OPEN,
    /** 신규 배팅이 마감되고 결과를 기다리는 상태다. */
    CLOSED,
    /** 사용자 배팅 정산까지 완료된 상태다. */
    SETTLED,
    /** 경기가 종료되어 배팅을 환불할 상태다. */
    CANCELLED
}
