package com.clutch.user.dto;

/** 보유 포인트 순위 한 행을 표현한다. */
public record PointRanking(
        int rank,
        String displayName,
        long point
) {
}
