package com.moara.moa.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 플랫폼(SYSTEM_ADMIN) 전용 진입. 설정된 은닉 경로({@link PlatformAccessProperties#getEntryPath()})로
 * 들어오면 세션에 플랫폼 플래그를 심고 로그인(2단계)으로 보낸다. 공개 /enter에는 이 경로가 노출되지 않는다.
 *
 * <p>확장 지점: 허용 IP 미통과 시 404로 존재를 숨긴다(향후 리버스 프록시/네트워크 분리와 병행).
 */
@Component
public class PlatformEntryFilter extends OncePerRequestFilter {
  private final PlatformAccessProperties properties;

  public PlatformEntryFilter(PlatformAccessProperties properties) {
    this.properties = properties;
  }

  /**
   * 은닉 진입 경로인지. <b>끝 슬래시는 같은 경로로 본다.</b>
   *
   * <p>전에는 {@code equals} 하나였다. 그래서 {@code /platform/} 로 들어오면 아무 안내 없이
   * {@code /enter}(기관 코드 화면)로 떨어졌고, 운영자는 "안 들어가진다"로 받아들였다 —
   * <b>끝 슬래시는 사람이 친 것이 아니라 브라우저와 자동완성이 붙인다.</b>
   *
   * <p>대소문자는 <b>가리지 않는다</b>({@code /Platform} 은 여전히 통과하지 않는다).
   * 이건 타이포이고, 은닉 경로는 적힌 그대로만 맞는 편이 낫다 — 운영자가 경로를
   * {@code MOA_PLATFORM_ENTRY_PATH} 로 바꿔 둘 수 있으므로 대소문자 변형까지 열어 줄 이유가 없다.
   */
  private boolean matchesEntryPath(HttpServletRequest request) {
    String expected = request.getContextPath() + properties.getEntryPath();
    String actual = request.getRequestURI();
    if (actual.equals(expected)) {
      return true;
    }
    // "/platform/" 처럼 끝 슬래시만 다른 경우. 설정값 자체가 "/" 로 끝나면 중복 비교가 아니다.
    return !expected.endsWith("/") && actual.equals(expected + "/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (matchesEntryPath(request)) {
      if (!properties.ipAllowed(request.getRemoteAddr())) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }
      EntrySession.selectPlatform(request);
      response.sendRedirect(request.getContextPath() + "/login");
      return;
    }
    chain.doFilter(request, response);
  }
}
