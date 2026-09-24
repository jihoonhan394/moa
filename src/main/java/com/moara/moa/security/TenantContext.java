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

  /**
   * 현재 요청의 기관 식별자. 인증 컨텍스트가 없으면 기본 기관을 돌려준다.
   *
   * <p>⚠️ <b>이 폴백은 의도적으로 유지한다.</b> {@code GlobalViewAdvice}가 {@code @ControllerAdvice}로
   * 로그인·기관선택 화면을 포함한 모든 뷰에서 실행되며 여기를 호출한다. fail-closed(예외)로 바꾸면
   * 인증 전 화면이 전부 깨진다(실제로 시도했다가 11개 테스트가 실패했다).
   *
   * <p>따라서 <b>기관 스코프를 실제로 강제하는 책임은 서비스 계층에 있다</b> — 리포지토리 쿼리에
   * tenant 조건을 넣고, 단건 접근은 소속을 재검증한다(예: {@code ManagedUserService.findById(tenantId, id)},
   * {@code UserLifecycleService.offboard}). 이 메서드의 반환값만 믿고 권한을 판단해서는 안 된다.
   */
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
