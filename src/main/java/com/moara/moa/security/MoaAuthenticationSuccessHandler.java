package com.moara.moa.security;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.group.AccessGroupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 로그인 성공 후 역할별 착지 분기. 플랫폼 관리자는 플랫폼 콘솔로, 관리 역할은 대시보드로, 그 외는 개인 허브로.
 *   SYSTEM_ADMIN → /admin · (TENANT_ADMIN·INFRA_MANAGER·ASSET_MANAGER) → /dashboard ·
 *   (역할 없는) 부서장 → /team · 그 외 → /my/workspace
 * <p>착지 경로는 <b>기능(FeatureModule)과 무관하게 항상 열려 있어야</b> 한다. 과거 /portal(SERVER_ACCESS)·
 * /assets(ASSETS) 착지는 해당 기능이 없는 기관에서 {@code TenantFeatureInterceptor}가 403을 냈다.
 * 대시보드·팀 콘솔·개인 허브는 기능 게이트가 없으므로 어떤 엔타이틀먼트 조합에서도 안전하다.
 * (관리자는 착지 후 사이드바에서 보유 기능의 화면으로 이동한다 — {@code HomeController.home()}과 동일한 방침.)
 */
@Component
public class MoaAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
  private final AccessGroupService groupService;
  private final AuditLogService auditLogService;

  public MoaAuthenticationSuccessHandler(AccessGroupService groupService, AuditLogService auditLogService) {
    this.groupService = groupService;
    this.auditLogService = auditLogService;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    String target = "/my/workspace";
    if (authentication.getPrincipal() instanceof MoaUserDetails user) {
      // 로그인 이력 기록(접속 이력 화면에서 조회). 기관 사용자만 — 플랫폼 관리자는 소속 기관이 없다.
      if (user.getTenantId() != null) {
        auditLogService.recordTenantAction(
            user.getTenantId(), user.getUserId(), "USER_LOGIN", "ManagedUser", user.getUserId(),
            AuditResult.SUCCESS, "ip=" + ClientIpResolver.resolve(request));
      }
      // 다중역할: 우선순위대로 착지(플랫폼 > 관리 역할 > 부서장 > 그 외). 모두 기능 게이트 없는 경로.
      if (user.hasRole(com.moara.moa.user.UserRole.SYSTEM_ADMIN)) {
        target = "/admin";
      } else if (user.hasRole(com.moara.moa.user.UserRole.TENANT_ADMIN)
          || user.hasRole(com.moara.moa.user.UserRole.INFRA_MANAGER)
          || user.hasRole(com.moara.moa.user.UserRole.ASSET_MANAGER)) {
        target = "/dashboard";
      } else if (user.getTenantId() != null
          && groupService.isDepartmentLeader(user.getTenantId(), user.getUserId())) {
        target = "/team"; // 관리 역할 없는 부서장의 관리 홈(팀 콘솔)
      } else {
        target = "/my/workspace"; // 일반 사용자: 항상 접근 가능한 개인 허브
      }
    }
    response.sendRedirect(request.getContextPath() + target);
  }
}
