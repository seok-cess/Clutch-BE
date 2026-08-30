package com.clutch.user.dto.response;

import com.clutch.user.dto.MyPointRanking;

/** 현재 사용자의 전체 보유 포인트 순위 응답이다. */
public record MyPointRankingResponse(
        long point,
        long rank
) {

    public static MyPointRankingResponse from(MyPointRanking ranking) {
        return new MyPointRankingResponse(ranking.point(), ranking.rank());
    }
}
