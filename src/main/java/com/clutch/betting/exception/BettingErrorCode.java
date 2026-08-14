package com.clutch.betting.exception;

public enum BettingErrorCode {
    EVENT_NOT_FOUND("배팅 이벤트를 찾을 수 없습니다."),
    EVENT_NOT_OPEN("현재 배팅할 수 없는 이벤트입니다."),
    LIVE_DATA_UNAVAILABLE("라이브 경기 정보를 확인할 수 없습니다."),
    INVALID_TEAM("배팅 이벤트 참가 팀만 선택할 수 있습니다."),
    DUPLICATE_BET("동일 세트에는 한 번만 배팅할 수 있습니다."),
    USER_NOT_FOUND("사용자를 찾을 수 없습니다."),
    INSUFFICIENT_POINT("보유 포인트가 부족합니다."),
    RESULT_NOT_READY("아직 배팅 결과가 확정되지 않았습니다."),
    EVENT_NOT_CANCELLED("취소된 배팅 이벤트만 환불할 수 있습니다.");

    private final String message;

    BettingErrorCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
