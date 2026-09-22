package com.moara.moa.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** 리버스 프록시 뒤 실제 클라이언트 IP 복원 우선순위 검증. */
class ClientIpResolverTest {

  @Test
  void prefersFirstPublicIpInForwardedForChain() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-Forwarded-For", "203.0.113.5, 192.168.0.1, 10.0.0.2");
    assertThat(ClientIpResolver.resolve(req)).isEqualTo("203.0.113.5");
  }

  @Test
  void fallsBackToFirstEntryWhenAllPrivate() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-Forwarded-For", "192.168.0.50, 192.168.0.1");
    assertThat(ClientIpResolver.resolve(req)).isEqualTo("192.168.0.50");
  }

  @Test
  void usesXRealIpWhenNoForwardedFor() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-Real-IP", "203.0.113.9");
    req.setRemoteAddr("192.168.0.1");
    assertThat(ClientIpResolver.resolve(req)).isEqualTo("203.0.113.9");
  }

  @Test
  void fallsBackToRemoteAddrWhenNoHeaders() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRemoteAddr("198.51.100.7");
    assertThat(ClientIpResolver.resolve(req)).isEqualTo("198.51.100.7");
  }
}
