package com.clutch.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PersonalDataMaskerTest {

    @ParameterizedTest
    @CsvSource({
            "김민준, 김*준",
            "남궁민수, 남**수",
            "김민, 김*",
            "김, *",
    })
    @DisplayName("이름은 첫 글자와 끝 글자만 남긴다")
    void 이름_마스킹(String raw, String expected) {
        assertEquals(expected, PersonalDataMasker.name(raw));
    }

    @Test
    @DisplayName("두 글자 이름은 성만 남는 것을 피해 끝 글자를 가린다")
    void 두글자_이름은_뒤를_가린다() {
        assertEquals("김*", PersonalDataMasker.name("김민"));
    }

    @ParameterizedTest
    @CsvSource({
            "01012345678, 010-****-5678",
            "010-1234-5678, 010-****-5678",
            "0212345678, 02-****-5678",
            "02-1234-5678, 02-****-5678",
    })
    @DisplayName("전화번호는 국번을 가리고 뒤 4자리를 남긴다")
    void 전화번호_마스킹(String raw, String expected) {
        assertEquals(expected, PersonalDataMasker.phone(raw));
    }

    @Test
    @DisplayName("전화번호가 너무 짧으면 전부 가린다")
    void 짧은_전화번호는_전부_가린다() {
        assertEquals("*******", PersonalDataMasker.phone("1234567"));
    }

    @ParameterizedTest
    @CsvSource({
            "hongildong@example.com, ho********@example.com",
            "abc@example.com, a**@example.com",
            "ab@example.com, a*@example.com",
            "a@example.com, *@example.com",
    })
    @DisplayName("이메일은 아이디를 가리고 도메인은 남긴다")
    void 이메일_마스킹(String raw, String expected) {
        assertEquals(expected, PersonalDataMasker.email(raw));
    }

    @Test
    @DisplayName("이메일 형식이 아니면 통째로 가린다 — 무엇이 담겼는지 알 수 없다")
    void 형식이_아닌_이메일은_전부_가린다() {
        assertEquals("*******", PersonalDataMasker.email("notmail"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("빈 값은 그대로 둔다 — 마스킹할 개인정보가 없다")
    void 빈값은_그대로(String raw) {
        assertEquals(raw, PersonalDataMasker.name(raw));
        assertEquals(raw, PersonalDataMasker.phone(raw));
        assertEquals(raw, PersonalDataMasker.email(raw));
    }

    @Test
    @DisplayName("마스킹 결과에 원본 조각이 그대로 남지 않는다")
    void 원본이_복원되지_않는다() {
        assertFalse(PersonalDataMasker.phone("01012345678").contains("1234"));
        assertFalse(PersonalDataMasker.name("김민준").contains("민"));
    }
}
