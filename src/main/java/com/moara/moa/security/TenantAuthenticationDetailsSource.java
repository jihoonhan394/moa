package com.moara.moa.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.stereotype.Component;

/** 로그인 요청에서 진입 세션(선택된 기관/플랫폼)을 읽어 인증 details로 싣는다. */
@Component
public class TenantAuthenticationDetailsSource
    implements AuthenticationDetailsSource<HttpServletRequest, TenantAuthenticationDetails> {

  @Override
  public TenantAuthenticationDetails buildDetails(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    return new TenantAuthenticationDetails(
        request, EntrySession.tenantId(session), EntrySession.isPlatform(session));
  }
}
