package com.clutch.betting.exception;

/** 배팅 유스케이스에서 클라이언트에 노출하는 오류 종류와 메시지다. */
public enum BettingErrorCode {
    /** 요청 본문이나 경로 값이 API 계약을 만족하지 않는다. */
    INVALID_REQUEST(Message.INVALID_REQUEST),
    /** 외부 매치 ID가 전달되지 않았다. */
    EXTERNAL_MATCH_ID_REQUIRED(Message.EXTERNAL_MATCH_ID_REQUIRED),
    /** 세트 번호가 허용 범위를 벗어났다. */
    INVALID_SET_NUMBER(Message.INVALID_SET_NUMBER),
    /** 첫 번째 참가 팀 ID가 전달되지 않았다. */
    FIRST_TEAM_ID_REQUIRED(Message.FIRST_TEAM_ID_REQUIRED),
    /** 두 번째 참가 팀 ID가 전달되지 않았다. */
    SECOND_TEAM_ID_REQUIRED(Message.SECOND_TEAM_ID_REQUIRED),
    /** 한 이벤트의 두 배팅 선택지가 같은 팀이다. */
    DUPLICATE_TEAM_OPTIONS(Message.DUPLICATE_TEAM_OPTIONS),
    /** 배팅 이벤트 오픈 시각이 전달되지 않았다. */
    EVENT_OPENED_AT_REQUIRED(Message.EVENT_OPENED_AT_REQUIRED),
    /** 외부 세트 ID가 전달되지 않았다. */
    EXTERNAL_GAME_ID_REQUIRED(Message.EXTERNAL_GAME_ID_REQUIRED),
    /** 이벤트에 기존 값과 다른 외부 세트 ID를 연결하려 했다. */
    EVENT_GAME_ALREADY_ATTACHED(Message.EVENT_GAME_ALREADY_ATTACHED),
    /** 세트 시작 후 배팅 가능 시간이 양수가 아니다. */
    INVALID_BETTING_DURATION(Message.INVALID_BETTING_DURATION),
    /** 상태 판단에 필요한 현재 시각이 전달되지 않았다. */
    CURRENT_TIME_REQUIRED(Message.CURRENT_TIME_REQUIRED),
    /** 승리 팀 ID가 전달되지 않았다. */
    WINNER_TEAM_ID_REQUIRED(Message.WINNER_TEAM_ID_REQUIRED),
    /** 이벤트 참가 팀이 아닌 팀을 승자로 지정했다. */
    WINNER_NOT_PARTICIPANT(Message.WINNER_NOT_PARTICIPANT),
    /** 승자 확정과 이벤트 종료 조건을 충족하지 못한 상태에서 정산했다. */
    EVENT_NOT_SETTLEABLE(Message.EVENT_NOT_SETTLEABLE),
    /** 사용자 배팅이 참조할 이벤트 ID가 전달되지 않았다. */
    BETTING_EVENT_ID_REQUIRED(Message.BETTING_EVENT_ID_REQUIRED),
    /** 사용자 ID가 전달되지 않았다. */
    USER_ID_REQUIRED(Message.USER_ID_REQUIRED),
    /** 사용자가 선택한 팀 ID가 전달되지 않았다. */
    SELECTED_TEAM_ID_REQUIRED(Message.SELECTED_TEAM_ID_REQUIRED),
    /** 배팅 금액이 최소 금액보다 작다. */
    BET_AMOUNT_TOO_LOW(Message.BET_AMOUNT_TOO_LOW),
    /** 배팅 금액이 최대 금액보다 크다. */
    BET_AMOUNT_TOO_HIGH(Message.BET_AMOUNT_TOO_HIGH),
    /** 배팅 금액이 허용 범위를 벗어났다. */
    BET_AMOUNT_OUT_OF_RANGE(Message.BET_AMOUNT_OUT_OF_RANGE),
    /** 이미 처리된 사용자 배팅을 다른 결과로 전환하려 했다. */
    USER_BET_NOT_PLACED(Message.USER_BET_NOT_PLACED),
    /** 포인트 거래가 참조할 사용자 배팅 ID가 전달되지 않았다. */
    USER_BET_ID_REQUIRED(Message.USER_BET_ID_REQUIRED),
    /** 포인트 거래 유형이 전달되지 않았다. */
    POINT_TRANSACTION_TYPE_REQUIRED(Message.POINT_TRANSACTION_TYPE_REQUIRED),
    /** 배팅 차감 거래의 포인트 증감 방향이 올바르지 않다. */
    INVALID_STAKE_POINT_DELTA(Message.INVALID_STAKE_POINT_DELTA),
    /** 지급 또는 환불 거래의 포인트 증감 방향이 올바르지 않다. */
    INVALID_CREDIT_POINT_DELTA(Message.INVALID_CREDIT_POINT_DELTA),
    /** 배팅 거래 금액이 양수가 아니다. */
    BET_AMOUNT_NOT_POSITIVE(Message.BET_AMOUNT_NOT_POSITIVE),
    /** 배팅 이벤트가 존재하지 않는다. */
    EVENT_NOT_FOUND(Message.EVENT_NOT_FOUND),
    /** 사용자가 등록한 배팅이 존재하지 않는다. */
    BET_NOT_FOUND(Message.BET_NOT_FOUND),
    /** 배팅 이벤트가 신규 배팅을 받을 수 없는 상태다. */
    EVENT_NOT_OPEN(Message.EVENT_NOT_OPEN),
    /** 배팅 판단에 필요한 라이브 데이터가 없거나 유효하지 않다. */
    LIVE_DATA_UNAVAILABLE(Message.LIVE_DATA_UNAVAILABLE),
    /** 이벤트 참가 팀이 아닌 팀을 선택했다. */
    INVALID_TEAM(Message.INVALID_TEAM),
    /** 동일 사용자가 같은 이벤트에 이미 배팅했다. */
    DUPLICATE_BET(Message.DUPLICATE_BET),
    /** 대상 사용자가 존재하지 않는다. */
    USER_NOT_FOUND(Message.USER_NOT_FOUND),
    /** 배팅 금액보다 보유 포인트가 적다. */
    INSUFFICIENT_POINT(Message.INSUFFICIENT_POINT),
    /** 세트 승자가 아직 확정되지 않았다. */
    RESULT_NOT_READY(Message.RESULT_NOT_READY),
    /** 취소되지 않은 이벤트에 환불을 요청했다. */
    EVENT_NOT_CANCELLED(Message.EVENT_NOT_CANCELLED);

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

    /** 애노테이션을 포함한 모든 배팅 오류 발생 지점에서 재사용하는 고정 메시지다. */
    public static final class Message {
        public static final String INVALID_REQUEST = "요청 값이 올바르지 않습니다.";
        public static final String EXTERNAL_MATCH_ID_REQUIRED = "외부 매치 ID는 필수입니다.";
        public static final String INVALID_SET_NUMBER = "세트 번호는 1 이상이어야 합니다.";
        public static final String FIRST_TEAM_ID_REQUIRED = "첫 번째 팀 ID는 필수입니다.";
        public static final String SECOND_TEAM_ID_REQUIRED = "두 번째 팀 ID는 필수입니다.";
        public static final String DUPLICATE_TEAM_OPTIONS = "배팅 선택지의 두 팀은 서로 달라야 합니다.";
        public static final String EVENT_OPENED_AT_REQUIRED = "배팅 오픈 시각은 필수입니다.";
        public static final String EXTERNAL_GAME_ID_REQUIRED = "외부 세트 ID는 필수입니다.";
        public static final String EVENT_GAME_ALREADY_ATTACHED = "이미 다른 세트 ID가 연결된 배팅 이벤트입니다.";
        public static final String INVALID_BETTING_DURATION = "세트 시작 후 배팅 가능 시간은 양수여야 합니다.";
        public static final String CURRENT_TIME_REQUIRED = "현재 시각은 필수입니다.";
        public static final String WINNER_TEAM_ID_REQUIRED = "승리 팀 ID는 필수입니다.";
        public static final String WINNER_NOT_PARTICIPANT = "승리 팀은 배팅 이벤트 참가 팀이어야 합니다.";
        public static final String EVENT_NOT_SETTLEABLE = "승리 팀이 확정된 종료 이벤트만 정산할 수 있습니다.";
        public static final String BETTING_EVENT_ID_REQUIRED = "배팅 이벤트 ID는 필수입니다.";
        public static final String USER_ID_REQUIRED = "사용자 ID는 필수입니다.";
        public static final String SELECTED_TEAM_ID_REQUIRED = "선택 팀 ID는 필수입니다.";
        public static final String BET_AMOUNT_TOO_LOW = "배팅 금액은 1,000포인트 이상이어야 합니다.";
        public static final String BET_AMOUNT_TOO_HIGH = "배팅 금액은 100,000포인트 이하여야 합니다.";
        public static final String BET_AMOUNT_OUT_OF_RANGE = "배팅 금액은 1,000포인트 이상 100,000포인트 이하여야 합니다.";
        public static final String USER_BET_NOT_PLACED = "등록 상태의 배팅만 정산할 수 있습니다.";
        public static final String USER_BET_ID_REQUIRED = "사용자 배팅 ID는 필수입니다.";
        public static final String POINT_TRANSACTION_TYPE_REQUIRED = "포인트 거래 유형은 필수입니다.";
        public static final String INVALID_STAKE_POINT_DELTA = "배팅 차감 포인트는 음수여야 합니다.";
        public static final String INVALID_CREDIT_POINT_DELTA = "지급 또는 환불 포인트는 양수여야 합니다.";
        public static final String BET_AMOUNT_NOT_POSITIVE = "배팅 금액은 양수여야 합니다.";
        public static final String EVENT_NOT_FOUND = "배팅 이벤트를 찾을 수 없습니다.";
        public static final String BET_NOT_FOUND = "사용자 배팅을 찾을 수 없습니다.";
        public static final String EVENT_NOT_OPEN = "현재 배팅할 수 없는 이벤트입니다.";
        public static final String LIVE_DATA_UNAVAILABLE = "라이브 경기 정보를 확인할 수 없습니다.";
        public static final String INVALID_TEAM = "배팅 이벤트 참가 팀만 선택할 수 있습니다.";
        public static final String DUPLICATE_BET = "동일 세트에는 한 번만 배팅할 수 있습니다.";
        public static final String USER_NOT_FOUND = "사용자를 찾을 수 없습니다.";
        public static final String INSUFFICIENT_POINT = "보유 포인트가 부족합니다.";
        public static final String RESULT_NOT_READY = "아직 배팅 결과가 확정되지 않았습니다.";
        public static final String EVENT_NOT_CANCELLED = "취소된 배팅 이벤트만 환불할 수 있습니다.";

        /** 메시지 상수 모음은 인스턴스화하지 않는다. */
        private Message() {
        }
    }
}
