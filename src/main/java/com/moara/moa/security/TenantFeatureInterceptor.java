package com.moara.moa.security;

import com.moara.moa.tenant.FeatureModule;
import com.moara.moa.tenant.Tenant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.moara.moa.tenant.TenantService;

/**
 * 기능 엔타이틀먼트 강제. 요청 경로가 특정 기능(모듈)에 속하면, 현재 기관이 그 기능을 보유해야 통과한다.
 * 메뉴 숨김만으론 직접 URL을 못 막으므로 여기가 실질 방어선이다(역할 RBAC 위에 얹힘).
 */
@Component
public class TenantFeatureInterceptor implements HandlerInterceptor {
  private final TenantContext tenantContext;
  private final TenantService tenantService;

  public TenantFeatureInterceptor(TenantContext tenantContext, TenantService tenantService) {
    this.tenantContext = tenantContext;
    this.tenantService = tenantService;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    FeatureModule required = requiredFeature(request.getRequestURI());
    if (required == null) {
      return true;
    }
    UUID tenantId = tenantContext.currentTenantId();
    try {
      Tenant tenant = tenantService.getById(tenantId);
      if (tenant.hasFeature(required)) {
        return true;
      }
    } catch (RuntimeException ignored) {
      // 기관 조회 실패 시에도 접근을 막는다(fail-closed).
    }
    response.sendError(HttpServletResponse.SC_FORBIDDEN);
    return false;
  }

  private FeatureModule requiredFeature(String uri) {
    if (matches(uri, "/assets") || matches(uri, "/servers") || matches(uri, "/server-status")) {
      return FeatureModule.ASSETS;
    }
    if (matches(uri, "/portal") || matches(uri, "/connections")) {
      return FeatureModule.SERVER_ACCESS;
    }
    if (matches(uri, "/solutions") || matches(uri, "/solution-sequences")) {
      return FeatureModule.SOLUTIONS;
    }
    if (matches(uri, "/credentials")) {
      return FeatureModule.CREDENTIALS;
    }
    if (matches(uri, "/inventory")) {
      return FeatureModule.INVENTORY;
    }
    if (matches(uri, "/shared-resources") || matches(uri, "/reservations")) {
      return FeatureModule.RESERVATION;
    }
    if (matches(uri, "/wiki")) {
      return FeatureModule.WIKI;
    }
    return null;
  }

  private boolean matches(String uri, String base) {
    return uri.equals(base) || uri.startsWith(base + "/");
  }
}
