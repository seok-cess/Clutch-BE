package com.clutch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ClutchApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void systemDefaultTimeZoneIsUtc() {
        assertThat(ZoneId.systemDefault().normalized()).isEqualTo(ZoneOffset.UTC);
    }

}
