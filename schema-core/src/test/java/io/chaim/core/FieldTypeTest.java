package io.chaim.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

public class FieldTypeTest {

  @Test
  void shouldHaveCorrectEnumValues() {
    FieldType[] values = FieldType.values();
    assertThat(values).hasSize(4);
    assertThat(values).extracting(FieldType::name)
        .containsExactlyInAnyOrder("string", "number", "bool", "timestamp");
  }

  @Test
  void shouldValidateValidTypes() {
    assertThat(FieldType.isValid("string")).isTrue();
    assertThat(FieldType.isValid("number")).isTrue();
    assertThat(FieldType.isValid("bool")).isTrue();
    assertThat(FieldType.isValid("timestamp")).isTrue();
  }

  @Test
  void shouldRejectInvalidTypes() {
    assertThat(FieldType.isValid("boolean")).isFalse();
    assertThat(FieldType.isValid("int")).isFalse();
    assertThat(FieldType.isValid("float")).isFalse();
    assertThat(FieldType.isValid("double")).isFalse();
    assertThat(FieldType.isValid("long")).isFalse();
    assertThat(FieldType.isValid("date")).isFalse();
    assertThat(FieldType.isValid("datetime")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "  ", "\t", "\n"})
  void shouldRejectEmptyOrWhitespaceTypes(String invalidType) {
    assertThat(FieldType.isValid(invalidType)).isFalse();
  }

  @Test
  void shouldRejectNullType() {
    assertThat(FieldType.isValid(null)).isFalse();
  }

  @Test
  void shouldRejectCaseVariations() {
    assertThat(FieldType.isValid("String")).isFalse();
    assertThat(FieldType.isValid("STRING")).isFalse();
    assertThat(FieldType.isValid("Number")).isFalse();
    assertThat(FieldType.isValid("BOOL")).isFalse();
    assertThat(FieldType.isValid("Timestamp")).isFalse();
  }

  @Test
  void shouldRejectPartialMatches() {
    assertThat(FieldType.isValid("str")).isFalse();
    assertThat(FieldType.isValid("num")).isFalse();
    assertThat(FieldType.isValid("bo")).isFalse();
    assertThat(FieldType.isValid("time")).isFalse();
  }

  @Test
  void shouldRejectSpecialCharacters() {
    assertThat(FieldType.isValid("string!")).isFalse();
    assertThat(FieldType.isValid("number@")).isFalse();
    assertThat(FieldType.isValid("bool#")).isFalse();
    assertThat(FieldType.isValid("timestamp$")).isFalse();
  }

  @Test
  void shouldRejectNumbers() {
    assertThat(FieldType.isValid("1")).isFalse();
    assertThat(FieldType.isValid("123")).isFalse();
    assertThat(FieldType.isValid("0")).isFalse();
  }

  @Test
  void shouldRejectCommonProgrammingTypes() {
    assertThat(FieldType.isValid("Integer")).isFalse();
    assertThat(FieldType.isValid("Long")).isFalse();
    assertThat(FieldType.isValid("Float")).isFalse();
    assertThat(FieldType.isValid("Double")).isFalse();
    assertThat(FieldType.isValid("BigDecimal")).isFalse();
    assertThat(FieldType.isValid("LocalDate")).isFalse();
    assertThat(FieldType.isValid("LocalDateTime")).isFalse();
    assertThat(FieldType.isValid("ZonedDateTime")).isFalse();
  }
}
