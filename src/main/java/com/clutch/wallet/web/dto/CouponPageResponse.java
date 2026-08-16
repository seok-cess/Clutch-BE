package com.clutch.wallet.web.dto;

import java.util.List;

public record CouponPageResponse (
    List<CouponResponse> items,
    String nextCursor,
    boolean hasNext
){}
