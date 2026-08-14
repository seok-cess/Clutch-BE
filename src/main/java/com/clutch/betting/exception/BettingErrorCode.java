package com.clutch.betting.exception;

/** 배팅 유스케이스에서 클라이언트에 노출하는 오류 종류와 메시지다. */
public enum BettingErrorCode {
    /** 배팅 이벤트가 존재하지 않는다. */
    EVENT_NOT_FOUND("배팅 이벤트를 찾을 수 없습니다."),
    /** 사용자가 등록한 배팅이 존재하지 않는다. */
    BET_NOT_FOUND("사용자 배팅을 찾을 수 없습니다."),
    /** 배팅 이벤트가 신규 배팅을 받을 수 없는 상태다. */
    EVENT_NOT_OPEN("현재 배팅할 수 없는 이벤트입니다."),
    /** 배팅 판단에 필요한 라이브 데이터가 없거나 유효하지 않다. */
    LIVE_DATA_UNAVAILABLE("라이브 경기 정보를 확인할 수 없습니다."),
    /** 이벤트 참가 팀이 아닌 팀을 선택했다. */
    INVALID_TEAM("배팅 이벤트 참가 팀만 선택할 수 있습니다."),
    /** 동일 사용자가 같은 이벤트에 이미 배팅했다. */
    DUPLICATE_BET("동일 세트에는 한 번만 배팅할 수 있습니다."),
    /** 대상 사용자가 존재하지 않는다. */
    USER_NOT_FOUND("사용자를 찾을 수 없습니다."),
    /** 배팅 금액보다 보유 포인트가 적다. */
    INSUFFICIENT_POINT("보유 포인트가 부족합니다."),
    /** 세트 승자가 아직 확정되지 않았다. */
    RESULT_NOT_READY("아직 배팅 결과가 확정되지 않았습니다."),
    /** 취소되지 않은 이벤트에 환불을 요청했다. */
    EVENT_NOT_CANCELLED("취소된 배팅 이벤트만 환불할 수 있습니다.");

    private final String message;

    /**
     * 오류 코드에 고정된 사용자 메시지를 연결한다.
     *
     * @param message 사용자에게 노출할 오류 메시지
     */
    BettingErrorCode(String message) {
        this.message = message;
    }

    /**
     * 오류 응답에 사용할 사용자 메시지를 반환한다.
     *
     * @return 오류 코드에 연결된 사용자 메시지
     */
    public String getMessage() {
        return message;
    }
}
