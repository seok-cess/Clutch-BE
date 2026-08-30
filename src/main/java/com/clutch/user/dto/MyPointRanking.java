package com.clutch.user.dto;

/** 현재 사용자의 보유 포인트와 전체 포인트 순위 조회 결과다. */
public record MyPointRanking(
        long point,
        long rank
) {
}
