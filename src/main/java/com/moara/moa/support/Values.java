package com.moara.moa.support;

import java.util.UUID;

/**
 * 사람이 채운 입력값을 저장·조회에 쓸 형태로 다듬는다.
 *
 * <p>웹 폼은 "비어 있음"을 빈 문자열로 보낸다. 그것을 그대로 저장하면 "값이 없다"와 "빈 값을
 * 넣었다"가 DB에서 구별되지 않고, 모든 조회 조건이 {@code null}과 {@code ""} 둘 다를 신경 써야
 * 한다. 그래서 경계에서 한 번 {@code null}로 정규화한다.
 *
 * <p>같은 세 줄이 엔티티 8개와 컨트롤러 6개에 흩어져 있었고, <b>이미 갈라져 있었다</b> —
 * 절반은 잘못된 UUID를 {@code null}로 흘렸고 절반은 예외를 던져 500 화면을 냈다. 판단이
 * 흩어지면 그렇게 된다.
 */
public final class Values {
  /** 표준 표기의 길이. 8-4-4-4-12 + 하이픈 4. */
  private static final int CANONICAL_LENGTH = 36;

  private Values() {}

  /** 비어 있으면 값이 없는 것으로 본다. 앞뒤 공백은 저장 전에 떼어낸다. */
  public static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /**
   * 선택 항목으로 넘어온 식별자를 읽는다. 고르지 않았거나({@code ""}) 값이 망가졌으면
   * <b>고르지 않은 것으로</b> 본다.
   *
   * <p>망가진 값에 예외를 던지면 500 화면이 나간다 — 폼을 다시 그려 주지도, 무엇이 문제인지
   * 알려 주지도 못한다. 이 함수를 쓰는 곳은 전부 담당 그룹·자격증명·상위 품목처럼 <b>선택</b>
   * 항목이고, 필수 식별자는 {@code @PathVariable UUID}로 받아 스프링이 400으로 거른다.
   */
  public static UUID optionalUuid(String value) {
    String trimmed = blankToNull(value);
    // UUID.fromString은 자리 수를 따지지 않는다 — 한 자 잘린 값을 예외 없이 받아
    // 전혀 다른 유효한 UUID로 만든다("...00000000000" → 00000000-0000-0000-0000-000000000000).
    // 붙여넣다 잘린 식별자가 조용히 다른 대상을 가리키는 것이 예외보다 나쁘므로 길이를 본다.
    if (trimmed == null || trimmed.length() != CANONICAL_LENGTH) {
      return null;
    }
    try {
      return UUID.fromString(trimmed);
    } catch (IllegalArgumentException malformed) {
      return null;
    }
  }
}
