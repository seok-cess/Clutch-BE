package com.clutch.user.dto.response;

import com.clutch.user.dto.PointRanking;

/** 전체 사용자 보유 포인트 순위 응답의 한 행이다. */
public record PointRankingResponse(
        int rank,
        String displayName,
        long point
) {

    public static PointRankingResponse from(PointRanking ranking) {
        return new PointRankingResponse(
                ranking.rank(),
                ranking.displayName(),
                ranking.point()
        );
    }
}
