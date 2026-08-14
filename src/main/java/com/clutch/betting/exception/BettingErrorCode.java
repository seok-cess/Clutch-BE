package com.clutch.betting.exception;

/** 배팅 유스케이스에서 클라이언트에 노출하는 오류 종류와 메시지다. */
public enum BettingErrorCode {
    EVENT_NOT_FOUND("배팅 이벤트를 찾을 수 없습니다."),
    BET_NOT_FOUND("사용자 배팅을 찾을 수 없습니다."),
    EVENT_NOT_OPEN("현재 배팅할 수 없는 이벤트입니다."),
    LIVE_DATA_UNAVAILABLE("라이브 경기 정보를 확인할 수 없습니다."),
    INVALID_TEAM("배팅 이벤트 참가 팀만 선택할 수 있습니다."),
    DUPLICATE_BET("동일 세트에는 한 번만 배팅할 수 있습니다."),
    USER_NOT_FOUND("사용자를 찾을 수 없습니다."),
    INSUFFICIENT_POINT("보유 포인트가 부족합니다."),
    RESULT_NOT_READY("아직 배팅 결과가 확정되지 않았습니다."),
    EVENT_NOT_CANCELLED("취소된 배팅 이벤트만 환불할 수 있습니다.");

    private final String message;

    /** 오류 코드에 고정된 사용자 메시지를 연결한다. */
    BettingErrorCode(String message) {
        this.message = message;
    }

    /** 오류 응답에 사용할 사용자 메시지를 반환한다. */
    public String getMessage() {
        return message;
    }
}
