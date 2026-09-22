package com.moara.moa.web;

import com.moara.moa.group.AccessGroupService;
import com.moara.moa.notification.Notification;
import com.moara.moa.notification.NotificationService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.tenant.FeatureModule;
import com.moara.moa.tenant.TenantService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 모든 화면 모델에 역할·컨텍스트 노출 플래그와 기관 기능 집합을 제공한다.
 * 동일 사이드바 뼈대에서 메뉴를 선별 노출하는 데 쓴다(라우트 접근 제어는 SecurityConfig/인터셉터가 담당).
 */
@ControllerAdvice
public class GlobalViewAdvice {
  private final TenantContext tenantContext;
  private final TenantService tenantService;
  private final NotificationService notificationService;
  private final AccessGroupService groupService;

  public GlobalViewAdvice(
      TenantContext tenantContext, TenantService tenantService,
      NotificationService notificationService, AccessGroupService groupService) {
    this.tenantContext = tenantContext;
    this.tenantService = tenantService;
    this.notificationService = notificationService;
    this.groupService = groupService;
  }

  /** 사이드바 알림 벨: 미읽음 수 + 최근 알림(드롭다운). */
  @ModelAttribute("unreadNotifications")
  public long unreadNotifications() {
    return notificationService.unreadCount(tenantContext.currentTenantId(), tenantContext.currentUserId());
  }

  @ModelAttribute("recentNotifications")
  public List<Notification> recentNotifications() {
    return notificationService.recent(tenantContext.currentTenantId(), tenantContext.currentUserId());
  }

  @ModelAttribute("isAdmin")
  public boolean isAdmin() {
    return hasAnyRole("ROLE_SYSTEM_ADMIN", "ROLE_TENANT_ADMIN");
  }

  /** 플랫폼 콘솔 컨텍스트: SYSTEM_ADMIN. 플랫폼 메뉴만 본다. */
  @ModelAttribute("isPlatformConsole")
  public boolean isPlatformConsole() {
    return hasAnyRole("ROLE_SYSTEM_ADMIN");
  }

  /** 기관 업무(사람·조직·거버넌스) 메뉴 컨텍스트: 기관 운영자(TENANT_ADMIN). */
  @ModelAttribute("inTenantContext")
  public boolean inTenantContext() {
    return hasAnyRole("ROLE_TENANT_ADMIN");
  }

  /** 인프라(서버·솔루션·자격증명·접근부여) 관리 메뉴 컨텍스트: 인프라 관리자(INFRA_MANAGER). */
  @ModelAttribute("isInfraManager")
  public boolean isInfraManager() {
    return hasAnyRole("ROLE_INFRA_MANAGER");
  }

  /** 자산(실물·SW 인벤토리) 관리 메뉴 컨텍스트: 자산 관리자(ASSET_MANAGER). 인벤토리 모듈 도입 시 사용. */
  @ModelAttribute("isAssetManager")
  public boolean isAssetManager() {
    return hasAnyRole("ROLE_ASSET_MANAGER");
  }

  /** 부서장(그룹 leader) 컨텍스트: 팀 온보딩 등 위임 메뉴 노출에 쓴다. DB 조회라 인증 사용자에 한해 판정. */
  @ModelAttribute("isDepartmentLeader")
  public boolean isDepartmentLeader() {
    if (hasAnyRole("ROLE_SYSTEM_ADMIN")) {
      return false;
    }
    try {
      return groupService.isDepartmentLeader(
          tenantContext.currentTenantId(), tenantContext.currentUserId());
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  /** 현재 기관이 보유한 기능 집합(이름). 사이드바가 기능 메뉴를 선별 노출하는 데 쓴다. */
  @ModelAttribute("tenantFeatures")
  public Set<String> tenantFeatures() {
    if (hasAnyRole("ROLE_SYSTEM_ADMIN")) {
      return Set.of(); // 플랫폼 콘솔은 기능 메뉴를 쓰지 않는다.
    }
    try {
      return tenantService.getById(tenantContext.currentTenantId()).getFeatures().stream()
          .map(FeatureModule::name)
          .collect(Collectors.toSet());
    } catch (RuntimeException ignored) {
      return Set.of();
    }
  }

  private boolean hasAnyRole(String... roles) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    Set<String> wanted = Set.of(roles);
    return authentication.getAuthorities().stream()
        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
        .anyMatch(wanted::contains);
  }
}
