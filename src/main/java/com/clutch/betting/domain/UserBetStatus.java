package com.clutch.betting.domain;

/** 사용자 배팅의 등록 및 최종 처리 결과 상태다. */
public enum UserBetStatus {
    /** 등록 후 결과를 기다리는 상태다. */
    PLACED,
    /** 선택 팀이 승리한 적중 상태다. */
    WON,
    /** 선택 팀이 패배한 실패 상태다. */
    LOST,
    /** 이벤트 취소로 포인트가 반환된 상태다. */
    REFUNDED
}
