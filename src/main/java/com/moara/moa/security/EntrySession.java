package com.moara.moa.security;

import com.moara.moa.tenant.Tenant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;

/**
 * 두레이식 2단계 진입(기관 선택 → 로그인)에서 선택된 기관/플랫폼 여부를 세션에 보관한다.
 * 인증 시점에 {@link TenantAuthenticationDetailsSource}가 이 값을 읽어 (기관+아이디)로 조회한다.
 */
public final class EntrySession {
  public static final String TENANT_ID = "MOA_ENTRY_TENANT_ID";
  public static final String TENANT_CODE = "MOA_ENTRY_TENANT_CODE";
  public static final String TENANT_NAME = "MOA_ENTRY_TENANT_NAME";
  public static final String PLATFORM = "MOA_ENTRY_PLATFORM";

  private EntrySession() {}

  public static void selectTenant(HttpServletRequest request, Tenant tenant) {
    HttpSession session = request.getSession(true);
    session.setAttribute(TENANT_ID, tenant.getId());
    session.setAttribute(TENANT_CODE, tenant.getCode());
    session.setAttribute(TENANT_NAME, tenant.getName());
    session.removeAttribute(PLATFORM);
  }

  public static void selectPlatform(HttpServletRequest request) {
    HttpSession session = request.getSession(true);
    session.setAttribute(PLATFORM, Boolean.TRUE);
    session.removeAttribute(TENANT_ID);
    session.removeAttribute(TENANT_CODE);
    session.removeAttribute(TENANT_NAME);
  }

  public static boolean isPlatform(HttpSession session) {
    return session != null && Boolean.TRUE.equals(session.getAttribute(PLATFORM));
  }

  public static UUID tenantId(HttpSession session) {
    return session == null ? null : (UUID) session.getAttribute(TENANT_ID);
  }

  public static String tenantCode(HttpSession session) {
    return session == null ? null : (String) session.getAttribute(TENANT_CODE);
  }

  public static String tenantName(HttpSession session) {
    return session == null ? null : (String) session.getAttribute(TENANT_NAME);
  }

  /** 기관 또는 플랫폼 중 하나라도 선택되었는지(= 로그인 2단계로 진입 가능한지). */
  public static boolean hasSelection(HttpSession session) {
    return isPlatform(session) || tenantId(session) != null;
  }
}
