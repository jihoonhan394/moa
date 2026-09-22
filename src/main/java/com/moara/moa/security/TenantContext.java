package com.moara.moa.security;

import com.moara.moa.tenant.Tenant;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 컨트롤러가 현재 로그인 사용자의 <b>유효 테넌트</b>를 얻기 위한 얇은 헬퍼.
 * Service 계층은 이 값을 파라미터로 받아 격리를 강제하므로(테스트 용이), 컨텍스트 조회는 여기에 모은다.
 *
 * <p>기관에 속한 사용자(USER/TENANT_ADMIN)는 계정의 tenant_id가 곧 컨텍스트다.
 * SYSTEM_ADMIN({@code tenantId=null})은 플랫폼 콘솔만 사용하며 기관 컨텍스트가 필요 없어 기본 테넌트로 처리한다.
 */
@Component
public class TenantContext {

  public MoaUserDetails currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof MoaUserDetails details) {
      return details;
    }
    return null;
  }

  public UUID currentTenantId() {
    MoaUserDetails user = currentUser();
    if (user != null && user.getTenantId() != null) {
      return user.getTenantId();
    }
    // SYSTEM_ADMIN(테넌트 없음)은 플랫폼 콘솔 전용이라 기관 컨텍스트가 없다. 기본 테넌트로 처리.
    return Tenant.DEFAULT_TENANT_ID;
  }

  /** 현재 로그인 사용자의 식별자(감사 로그 행위자용). 인증 컨텍스트가 없으면 null. */
  public UUID currentUserId() {
    MoaUserDetails user = currentUser();
    return user != null ? user.getUserId() : null;
  }
}
