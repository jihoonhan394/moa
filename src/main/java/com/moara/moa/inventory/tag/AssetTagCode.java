package com.moara.moa.inventory.tag;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * 자산에 붙이는 관리번호(= QR 스티커에 인쇄되는 코드).
 *
 * <p><b>관리번호와 QR 코드는 같은 것이다.</b> 따로 두면 한 장비에 식별자가 둘 생기고, 현장에서
 * "이 번호로 찾아 주세요"가 통하지 않는다. 그래서 사람이 전화로 부를 수 있는 형태여야 한다.
 *
 * <pre>
 *   MOA-7K3F-2M9B
 *   └─┘ └───────┘
 *   접두  7자 난수 + 1자 검사문자
 * </pre>
 *
 * <ul>
 *   <li><b>혼동 문자를 뺀 32자 알파벳</b>(Crockford Base32) — {@code I L O U} 가 없다.
 *       {@code 1/I/L}, {@code 0/O} 를 사람이 받아 적을 때 틀리는 것을 없앤다.
 *   <li><b>난수</b>다. 순번이면 자산 수가 드러나고 옆 스티커 번호를 추측할 수 있다.
 *   <li><b>검사문자 1자</b> — 한 글자 오타와 이웃한 두 글자가 뒤바뀐 경우를 잡는다.
 *   <li><b>URL 을 넣지 않는다.</b> 스티커를 환경과 무관하게 미리 대량 인쇄해야 하고,
 *       QR 에 내부 FQDN 이 인쇄되면 그것만으로 망 구조가 드러난다.
 * </ul>
 */
public final class AssetTagCode {

  /** Crockford Base32 — I·L·O·U 가 없다. */
  private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

  private static final int RADIX = ALPHABET.length();
  private static final String PREFIX = "MOA";
  private static final int PAYLOAD_LENGTH = 7;
  private static final int BODY_LENGTH = PAYLOAD_LENGTH + 1;
  private static final RandomGenerator DEFAULT_RANDOM = new SecureRandom();

  private AssetTagCode() {}

  /** 새 코드를 만든다. 결과는 표시 형태({@code MOA-XXXX-XXXX})다. */
  public static String generate() {
    return generate(DEFAULT_RANDOM);
  }

  /** 테스트에서 난수원을 고정할 수 있게 둔다. */
  public static String generate(RandomGenerator random) {
    StringBuilder payload = new StringBuilder(PAYLOAD_LENGTH);
    for (int index = 0; index < PAYLOAD_LENGTH; index++) {
      payload.append(ALPHABET.charAt(random.nextInt(RADIX)));
    }
    return format(payload.append(checkCharacter(payload)).toString());
  }

  /**
   * 사람이 입력했거나 스캐너가 읽은 값을 표시 형태로 되돌린다.
   *
   * <p>소문자·공백·붙임표를 받아 주고, 혼동 문자({@code I L} → {@code 1}, {@code O} → {@code 0})도
   * 고쳐 준다. 형식이나 검사문자가 맞지 않으면 {@code null} 이다 — <b>예외를 던지지 않는다.</b>
   * 잘못 읽힌 코드는 오류가 아니라 "그런 코드는 없다"로 다루는 편이 화면이 단순해진다.
   */
  public static String normalize(String raw) {
    if (raw == null) {
      return null;
    }
    // ⚠️ 접두사를 글자 단위로 걸러 내면 안 된다. M·O·A 는 전부 코드 알파벳에 있는 글자라
    // 코드의 일부로 먹힌다. 먼저 구분자를 지우고 접두사를 통째로 떼어 낸다.
    String cleaned = raw.toUpperCase().replaceAll("[\\s_\\-]", "");
    String rest;
    if (cleaned.length() == BODY_LENGTH) {
      rest = cleaned;
    } else if (cleaned.length() == PREFIX.length() + BODY_LENGTH && cleaned.startsWith(PREFIX)) {
      rest = cleaned.substring(PREFIX.length());
    } else {
      return null;
    }

    StringBuilder body = new StringBuilder(BODY_LENGTH);
    for (char character : rest.toCharArray()) {
      char mapped = mapConfusable(character);
      if (ALPHABET.indexOf(mapped) < 0) {
        return null;
      }
      body.append(mapped);
    }
    String payload = body.substring(0, PAYLOAD_LENGTH);
    if (body.charAt(PAYLOAD_LENGTH) != checkCharacter(payload)) {
      return null;
    }
    return format(body.toString());
  }

  /** 표시 형태로 보이는 값인가. 저장 전 확인용. */
  public static boolean isValid(String raw) {
    return normalize(raw) != null;
  }

  private static String format(String body) {
    return PREFIX + "-" + body.substring(0, 4) + "-" + body.substring(4);
  }

  /**
   * 자리마다 다른 <b>홀수</b> 가중치를 곱해 더한 뒤 32 로 나눈 나머지.
   *
   * <p>가중치가 홀수여야 한다. 32 는 2 의 거듭제곱이라 가중치가 짝수면 그 자리의 글자를 특정
   * 간격(예: 16)만큼 바꿔도 나머지가 같아져 <b>한 글자 오류가 그냥 통과한다.</b> 홀수는 32 와
   * 서로소라 어떤 한 글자가 바뀌어도 반드시 나머지가 달라진다. (처음에 {@code index + 2} 를
   * 썼다가 테스트가 이것을 잡았다.)
   *
   * <p>가중치가 자리마다 달라야 <b>이웃한 두 글자가 뒤바뀐 경우</b>도 걸린다. 단순 합이면
   * {@code 7K} 와 {@code K7} 이 같은 검사문자를 갖는다.
   *
   * <p><b>한계</b>: 홀수 가중치끼리의 차는 항상 짝수라, 뒤바뀐 두 글자의 값 차이가 정확히 16 인
   * 경우만 걸러지지 않는다(전체 전치의 약 3%). 전부 잡으려면 32 진수를 버려야 하는데, QR 자체에
   * 오류 정정이 있고 이 검사문자는 <b>사람이 손으로 입력할 때</b>의 안전망이므로 거기까지 가지
   * 않는다.
   */
  private static char checkCharacter(CharSequence payload) {
    int sum = 0;
    for (int index = 0; index < payload.length(); index++) {
      sum += (2 * index + 1) * ALPHABET.indexOf(payload.charAt(index));
    }
    return ALPHABET.charAt(Math.floorMod(sum, RADIX));
  }

  private static char mapConfusable(char upper) {
    return switch (upper) {
      case 'I', 'L' -> '1';
      case 'O' -> '0';
      default -> upper;
    };
  }
}
