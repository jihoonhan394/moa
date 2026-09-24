package com.moara.moa.wiki;

import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 위키 컨트롤러들이 공유하는 인가 판단. 컨트롤러를 URL 경계로 쪼개면서 "현재 기관·사용자 + 공간 권한"
 * 확인이 파일마다 복사되지 않도록 한곳에 모았다.
 *
 * <p>권한 규칙 자체는 {@link WikiAccessService}에 있다. 여기는 요청 맥락(기관 ID·사용자 ID·기관
 * 관리자 여부)을 채워 호출하고, 거부를 {@link AccessDeniedException}으로 바꾸는 얇은 층이다.
 * 규칙을 바꾸려면 이 클래스가 아니라 {@code WikiAccessService}를 고쳐야 한다.
 */
@Component
class WikiAccessGuard {
  private final WikiAccessService accessService;
  private final TenantContext tenantContext;

  WikiAccessGuard(WikiAccessService accessService, TenantContext tenantContext) {
    this.accessService = accessService;
    this.tenantContext = tenantContext;
  }

  UUID tenantId() {
    return tenantContext.currentTenantId();
  }

  UUID userId() {
    return tenantContext.currentUserId();
  }

  boolean tenantAdmin() {
    MoaUserDetails user = tenantContext.currentUser();
    return user != null && user.hasRole(UserRole.TENANT_ADMIN);
  }

  List<WikiSpace> accessibleSpaces() {
    return accessService.accessibleSpaces(tenantId(), userId(), tenantAdmin());
  }

  boolean canView(UUID spaceId) {
    return accessService.canView(tenantId(), userId(), tenantAdmin(), spaceId);
  }

  boolean canEdit(UUID spaceId) {
    return accessService.canEdit(tenantId(), userId(), tenantAdmin(), spaceId);
  }

  boolean canManage(UUID spaceId) {
    return accessService.canManage(tenantId(), userId(), tenantAdmin(), spaceId);
  }

  void requireView(UUID spaceId) {
    if (!canView(spaceId)) {
      throw new AccessDeniedException("이 위키 공간을 볼 권한이 없습니다.");
    }
  }

  void requireEdit(UUID spaceId) {
    if (!canEdit(spaceId)) {
      throw new AccessDeniedException("이 위키 공간에 편집 권한이 없습니다.");
    }
  }

  void requireManage(UUID spaceId) {
    if (!canManage(spaceId)) {
      throw new AccessDeniedException("이 위키 공간을 관리할 권한이 없습니다.");
    }
  }
}
