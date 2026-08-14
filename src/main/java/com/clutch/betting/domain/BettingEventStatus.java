package com.clutch.betting.domain;

/** 배팅 이벤트의 오픈·종료·정산·취소 생명주기 상태다. */
public enum BettingEventStatus {
    OPEN,
    CLOSED,
    SETTLED,
    CANCELLED
}
