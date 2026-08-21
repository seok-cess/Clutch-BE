package com.clutch.wallet.web.dto;

import com.clutch.wallet.web.exception.InvalidCouponQueryException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class CouponCursorTest {

    @Test
    void 커서가_없으면_빈_커서를_반환한다(){
        CouponCursor cursor = CouponCursor.parse(null);
        assertNull(cursor.expiresAt());
        assertNull(cursor.id());
    }

    @Test
    void 정상_커서를_파싱한다(){
        CouponCursor cursor = CouponCursor.parse("1734000000000_57");
        assertEquals(57L, cursor.id());
        assertEquals(Instant.ofEpochMilli(1734000000000L), cursor.expiresAt());
    }

    @Test
    void 언더바가_없으면_예외(){
        assertThrows(InvalidCouponQueryException.class, () -> CouponCursor.parse("12345"));
    }

    @Test
    void timestamp가_숫자가_아니라면_예외(){
        assertThrows(InvalidCouponQueryException.class, () -> CouponCursor.parse("abc_57"));
    }

    @Test
    void id가_숫자가_아니면_예외(){
        assertThrows(InvalidCouponQueryException.class, () -> CouponCursor.parse("1734000000000_abc"));
    }
}
