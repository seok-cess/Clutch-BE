package com.clutch.wallet.kafka;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
public class CouponClaimAcceptedConsumerTest {
    private final CouponClaimAcceptedConsumer consumer = new CouponClaimAcceptedConsumer(null, null);

    @Test
    void 메시지가_있으면_그대로_반환한다(){
        assertEquals("문제 발생", consumer.resolveFailureReason(new RuntimeException("문제 발생")));
    }

    @Test
    void 메시지가_null이면_클래스명을_반환한다(){
        assertEquals("NullPointerException",
                consumer.resolveFailureReason(new NullPointerException()));
    }

    @Test
    void 메시지가_빈문자열이면_클래스명을_반환한다(){
        assertEquals("IllegalStateException",
                consumer.resolveFailureReason(new IllegalStateException("")));
    }

    @Test
    void 메시지가_공백이면_클래스명을_반환한다(){
        assertEquals("IllegalStateException",
                consumer.resolveFailureReason(new IllegalStateException("   ")));
    }
}