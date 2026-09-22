package com.moara.moa.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** TOTP(RFC 6238, Google Authenticator 호환) 코드 생성·검증과 시계오차 허용을 검증(순수). */
class TotpServiceTest {
  private final TotpService service = new TotpService();

  @Test
  void generatedCodeVerifiesAndWrongCodeFails() {
    String secret = service.generateSecret();
    long now = 1_700_000_000_000L; // 고정 시각(결정적)
    String code = service.codeAt(secret, now);

    assertThat(code).matches("\\d{6}");
    assertThat(service.verify(secret, code, now)).isTrue();
    assertThat(service.verify(secret, "000000", now)).isFalse();
    assertThat(service.verify(secret, "abc", now)).isFalse();
  }

  @Test
  void toleratesOneStepClockDriftButNotMore() {
    String secret = service.generateSecret();
    long now = 1_700_000_000_000L;
    String code = service.codeAt(secret, now);

    // ±30초(1스텝)는 허용.
    assertThat(service.verify(secret, code, now + 29_000L)).isTrue();
    assertThat(service.verify(secret, code, now - 29_000L)).isTrue();
    // 90초(3스텝)는 불허.
    assertThat(service.verify(secret, code, now + 90_000L)).isFalse();
  }

  @Test
  void provisioningUriIsAuthenticatorCompatible() {
    String uri = service.provisioningUri("MOA", "alice", "JBSWY3DPEHPK3PXP");
    assertThat(uri).startsWith("otpauth://totp/");
    assertThat(uri).contains("secret=JBSWY3DPEHPK3PXP").contains("issuer=MOA").contains("digits=6").contains("period=30");
  }
}
