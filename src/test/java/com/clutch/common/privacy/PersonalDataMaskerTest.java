package com.clutch.common.privacy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalDataMaskerTest {

    private final PersonalDataMasker masker = new PersonalDataMasker();

    @Test
    void 이름_이메일_전화번호를_응답용으로_마스킹한다() {
        assertThat(masker.maskName("김현정")).isEqualTo("김*정");
        assertThat(masker.maskEmail("user001@example.com"))
                .isEqualTo("use***@example.com");
        assertThat(masker.maskPhoneNumber("010-1234-5678"))
                .isEqualTo("010-****-5678");
    }

    @Test
    void 개인정보가_없으면_null을_반환한다() {
        assertThat(masker.maskName(null)).isNull();
        assertThat(masker.maskEmail(" ")).isNull();
        assertThat(masker.maskPhoneNumber(null)).isNull();
    }
}
