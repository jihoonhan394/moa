package com.moara.moa.security;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * 시간 기반 일회용 비밀번호(TOTP, RFC 6238) — <b>Google Authenticator 호환</b>(HMAC-SHA1, 30초, 6자리).
 * 외부 라이브러리 없이 표준 구현. 이 서비스는 순수 계산만 담당(저장·암호화는 {@code TwoFactorService}).
 * 2단계 인증의 <b>토대</b>: 시크릿 생성·프로비저닝 URI·코드 검증. (민감 작업 게이팅 연결은 후속.)
 */
@Service
public class TotpService {
  private static final int STEP_SECONDS = 30;
  private static final int DIGITS = 6;
  private static final int SECRET_BYTES = 20; // 160-bit
  private static final SecureRandom RANDOM = new SecureRandom();

  /** 새 시크릿(Base32). 사용자가 인증기 앱에 등록. */
  public String generateSecret() {
    byte[] bytes = new byte[SECRET_BYTES];
    RANDOM.nextBytes(bytes);
    return Base32.encode(bytes);
  }

  /** 인증기 앱 등록용 otpauth URI(QR/수동키). issuer=서비스명, account=사용자 식별. */
  public String provisioningUri(String issuer, String account, String base32Secret) {
    String label = urlEncode(issuer) + ":" + urlEncode(account);
    return "otpauth://totp/" + label
        + "?secret=" + base32Secret
        + "&issuer=" + urlEncode(issuer)
        + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
  }

  /** 주어진 시각(ms)의 코드. 테스트/검증 공용. */
  public String codeAt(String base32Secret, long timeMillis) {
    long counter = (timeMillis / 1000L) / STEP_SECONDS;
    byte[] key = Base32.decode(base32Secret);
    byte[] hash = hmacSha1(key, ByteBuffer.allocate(8).putLong(counter).array());
    int offset = hash[hash.length - 1] & 0x0f;
    int binary = ((hash[offset] & 0x7f) << 24)
        | ((hash[offset + 1] & 0xff) << 16)
        | ((hash[offset + 2] & 0xff) << 8)
        | (hash[offset + 3] & 0xff);
    int otp = binary % (int) Math.pow(10, DIGITS);
    return String.format("%0" + DIGITS + "d", otp);
  }

  /** 코드 검증(시계 오차 허용: 앞뒤 1스텝). */
  public boolean verify(String base32Secret, String code, long timeMillis) {
    if (code == null || !code.trim().matches("\\d{" + DIGITS + "}")) {
      return false;
    }
    String c = code.trim();
    for (int w = -1; w <= 1; w++) {
      if (codeAt(base32Secret, timeMillis + (long) w * STEP_SECONDS * 1000L).equals(c)) {
        return true;
      }
    }
    return false;
  }

  private static byte[] hmacSha1(byte[] key, byte[] data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key, "HmacSHA1"));
      return mac.doFinal(data);
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC-SHA1 unavailable", exception);
    }
  }

  private static String urlEncode(String s) {
    return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
  }
}
