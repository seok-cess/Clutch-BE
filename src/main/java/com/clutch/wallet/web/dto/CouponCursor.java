package com.clutch.wallet.web.dto;

import com.clutch.wallet.web.exception.InvalidCouponQueryException;

import java.time.Instant;

public record CouponCursor(Instant expiresAt, Long id) {
    public static CouponCursor parse(String cursor){
        if(cursor == null || cursor.isBlank()){
            return new CouponCursor(null, null);
        }
        String[] parts = cursor.split("_", 2);
        if(parts.length != 2){
            throw new InvalidCouponQueryException("cursor 형식이 올바르지 않습니다.");
        }
        try{
            Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(parts[0]));
            Long id = Long.valueOf(parts[1]);
            return new CouponCursor(expiresAt, id);
        }catch(NumberFormatException e){
            throw new InvalidCouponQueryException("cursor 형식이 올바르지 않습니다.", e);
        }
    }
}
