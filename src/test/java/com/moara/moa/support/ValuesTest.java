package com.moara.moa.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 입력 정규화의 판단을 고정한다.
 *
 * <p>이 함수들이 한 곳으로 모이기 전에는 같은 이름의 복사본 14개가 있었고 동작이 갈라져
 * 있었다 — 절반은 잘못된 식별자를 걸러 냈고 절반은 예외를 던져 500 화면을 냈다. 어느 쪽이
 * 맞는지 코드만 봐서는 알 수 없었다. 여기가 그 답이다.
 */
class ValuesTest {
  @Test
  void blankBecomesNullAndEdgesAreTrimmed() {
    assertNull(Values.blankToNull(null));
    assertNull(Values.blankToNull(""));
    assertNull(Values.blankToNull("   "));
    assertNull(Values.blankToNull("\t\n"));
    assertEquals("서버실 A", Values.blankToNull("  서버실 A  "));
  }

  @Test
  void optionalUuidReadsASelection() {
    UUID id = UUID.randomUUID();
    assertEquals(id, Values.optionalUuid(id.toString()));
    assertEquals(id, Values.optionalUuid("  " + id + "  "));
  }

  /**
   * 안 골랐거나 값이 망가졌으면 안 고른 것으로 본다. 예외를 던지면 500 화면이 나가는데,
   * 그것은 폼을 다시 그려 주지도 무엇이 문제인지 알려 주지도 못한다.
   */
  @Test
  void optionalUuidTreatsGarbageAsNotSelected() {
    assertNull(Values.optionalUuid(null));
    assertNull(Values.optionalUuid(""));
    assertNull(Values.optionalUuid("   "));
    assertNull(Values.optionalUuid("없음"));
    assertNull(Values.optionalUuid("123"));
    // UUID.fromString 혼자서는 이 값을 예외 없이 받아 전혀 다른 UUID(전부 0)로 만든다.
    assertNull(Values.optionalUuid("00000000-0000-0000-0000-00000000000")); // 한 자 짧다
    assertNull(Values.optionalUuid("1-2-3-4-5"));
  }
}
