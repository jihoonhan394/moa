package com.moara.moa.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 출발지 판정. 로그인 잠금과 감사 기록이 이 값을 쓰므로, <b>클라이언트가 보낸 헤더를 어디까지
 * 믿는지</b>가 그대로 보안 경계가 된다.
 */
class ClientIpResolverTest {

  private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(remoteAddr);
    if (forwardedFor != null) {
      request.addHeader("X-Forwarded-For", forwardedFor);
    }
    return request;
  }

  @Nested
  @DisplayName("프록시가 없다고 선언한 환경 (기본값 0)")
  class NoTrustedProxy {

    private final ClientIpResolver resolver = new ClientIpResolver(0);

    @Test
    @DisplayName("⚠️ 헤더를 아예 믿지 않는다 — 위조해도 연결 상대 주소가 나온다")
    void 헤더를_믿지_않는다() {
      assertThat(resolver.resolve(request("198.51.100.7", "1.2.3.4")))
          .isEqualTo("198.51.100.7");
      assertThat(resolver.resolve(request("198.51.100.7", "1.2.3.4, 5.6.7.8")))
          .isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("⚠️ 잠금 우회 시도 — 매번 다른 값을 보내도 같은 출발지로 집계된다")
    void 잠금을_우회할_수_없다() {
      for (int attempt = 0; attempt < 5; attempt++) {
        assertThat(resolver.resolve(request("198.51.100.7", "203.0.113." + attempt)))
            .isEqualTo("198.51.100.7");
      }
    }

    @Test
    @DisplayName("헤더가 없으면 당연히 연결 상대 주소")
    void 헤더가_없으면_원격주소() {
      assertThat(resolver.resolve(request("198.51.100.7", null))).isEqualTo("198.51.100.7");
    }
  }

  @Nested
  @DisplayName("신뢰 홉 1개 (nginx 한 대)")
  class SingleTrustedProxy {

    private final ClientIpResolver resolver = new ClientIpResolver(1);

    @Test
    @DisplayName("프록시가 덮어쓴 경우($remote_addr) — 그 값이 출발지다")
    void 덮어쓰기_설정() {
      assertThat(resolver.resolve(request("10.0.0.2", "203.0.113.9"))).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("⚠️ 프록시가 덧붙인 경우($proxy_add_x_forwarded_for) — 앞의 위조값은 무시된다")
    void 덧붙이기_설정에서도_안전하다() {
      // 공격자가 보낸 "1.2.3.4" 가 체인 앞에 남지만, 끝에서 1번째가 프록시가 본 실제 주소다.
      assertThat(resolver.resolve(request("10.0.0.2", "1.2.3.4, 203.0.113.9")))
          .isEqualTo("203.0.113.9");
      assertThat(resolver.resolve(request("10.0.0.2", "1.2.3.4, 5.6.7.8, 203.0.113.9")))
          .isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("⚠️ 남의 IP 를 감사에 남기려는 시도 — 체인 앞에 끼워 넣어도 안 걸린다")
    void 감사_기록을_오염시킬_수_없다() {
      String victim = "203.0.113.200";
      assertThat(resolver.resolve(request("10.0.0.2", victim + ", 203.0.113.9")))
          .isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("헤더가 없으면 연결 상대 주소로 떨어진다")
    void 헤더가_없으면_원격주소() {
      assertThat(resolver.resolve(request("10.0.0.2", null))).isEqualTo("10.0.0.2");
      assertThat(resolver.resolve(request("10.0.0.2", "   "))).isEqualTo("10.0.0.2");
    }
  }

  @Nested
  @DisplayName("신뢰 홉 2개 (CDN/WAF 한 겹 더)")
  class TwoTrustedProxies {

    private final ClientIpResolver resolver = new ClientIpResolver(2);

    @Test
    @DisplayName("끝에서 2번째가 출발지다")
    void 끝에서_두번째() {
      assertThat(resolver.resolve(request("10.0.0.2", "203.0.113.9, 10.0.0.8")))
          .isEqualTo("203.0.113.9");
      assertThat(resolver.resolve(request("10.0.0.2", "1.2.3.4, 203.0.113.9, 10.0.0.8")))
          .isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("⚠️ 체인이 선언한 홉보다 짧으면 헤더를 믿지 않는다 — 설정과 실제가 어긋난 것이다")
    void 체인이_짧으면_믿지_않는다() {
      assertThat(resolver.resolve(request("10.0.0.2", "203.0.113.9"))).isEqualTo("10.0.0.2");
    }
  }

  @Test
  @DisplayName("비정상적으로 긴 값은 잘라낸다 — client_ip 는 64자다")
  void 긴_값은_잘린다() {
    String absurd = "x".repeat(500);
    String resolved = new ClientIpResolver(1).resolve(request("10.0.0.2", absurd));
    assertThat(resolved).hasSize(64);
  }

  @Test
  @DisplayName("음수 설정은 0 으로 본다 — 안전한 쪽")
  void 음수는_0으로() {
    assertThat(new ClientIpResolver(-3).resolve(request("198.51.100.7", "1.2.3.4")))
        .isEqualTo("198.51.100.7");
  }
}
