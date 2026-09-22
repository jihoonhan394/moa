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

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getRequestURI().equals(request.getContextPath() + properties.getEntryPath())) {
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
