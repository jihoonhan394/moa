package com.moara.moa.security;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * 로그인이 실패했을 때 세고, 남기고, 돌려보낸다.
 *
 * <p>전에는 실패가 아무 데도 남지 않았다 — 성공만 감사에 기록됐다. 그래서 "어젯밤에 누가
 * 우리 계정을 두드렸나"에 답할 수 없었고, 무한히 시도할 수도 있었다. AGENTS.md는 로그인
 * 실패도 기록하라고 적어 두었으니, 우리 규칙을 우리가 안 지키던 자리다.
 */
@Component
public class MoaAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {
  private final LoginThrottle throttle;
  private final AuditLogService auditLogService;
  private final ManagedUserRepository userRepository;

  public MoaAuthenticationFailureHandler(
      LoginThrottle throttle, AuditLogService auditLogService,
      ManagedUserRepository userRepository) {
    super("/login?error");
    this.throttle = throttle;
    this.auditLogService = auditLogService;
    this.userRepository = userRepository;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, jakarta.servlet.ServletException {
    // 폼 필드 이름은 username이지만, 기관 사용자가 실제로 넣는 값은 이메일이다.
    String submitted = request.getParameter("username");
    String clientIp = ClientIpResolver.resolve(request);
    UUID tenantId = EntrySession.tenantId(request.getSession(false));

    // 잠금에 막힌 시도는 다시 세지 않는다 — 세면 잠긴 문을 두드리는 것만으로 잠금이 무한히
    // 연장돼 시간이 지나도 풀리지 않는다. 감사에도 남기지 않는다: 잠긴 뒤에도 초당 수십 번
    // 두드릴 수 있어, 그걸 전부 적으면 공격자가 우리 감사 테이블을 채우는 수단이 된다.
    // 잠기기까지의 실패는 이미 남아 있고, 그것이 신호다.
    if (!(exception instanceof LoginLockedException) && submitted != null && !submitted.isBlank()) {
      throttle.recordFailure(tenantId, submitted, clientIp);
      audit(tenantId, submitted, clientIp);
    }
    super.onAuthenticationFailure(request, response, exception);
  }

  /**
   * 실패를 감사에 남긴다.
   *
   * <p>입력한 식별자가 실재하는 계정이면 그 사람을 행위자로, 아니면 행위자 없이 남긴다.
   * <b>실재하지 않는 계정으로의 시도가 오히려 더 중요한 신호다</b> — 계정을 훑는 공격이
   * 정확히 그 형태라, 그것을 못 적으면 가장 보고 싶은 공격이 감사에서 통째로 빠진다
   * (V72에서 행위자를 선택 항목으로 바꾼 이유).
   *
   * <p>기관을 고르지 않은 시도(진입 1단계를 건너뛴 요청)는 남길 기관이 없어 넘어간다.
   */
  private void audit(UUID tenantId, String submitted, String clientIp) {
    if (tenantId == null) {
      return;
    }
    // 기관 사용자의 로그인 키는 <b>이메일</b>이다(username은 표시용 이름이라 로그인에 쓰이지
    // 않는다 — MoaUserDetailsService 참고). 여기서 username으로 찾으면 실재하는 계정을 상대로
    // 한 시도조차 늘 "행위자 없음"으로 남아, 누가 표적인지 감사에서 알 수 없게 된다.
    UUID actorId = userRepository.findByTenantIdAndEmailIgnoreCase(tenantId, submitted)
        .map(ManagedUser::getId).orElse(null);
    auditLogService.recordTenantAction(
        tenantId, actorId, "USER_LOGIN_FAIL", "ManagedUser", actorId,
        AuditResult.FAILURE, "ip=" + clientIp + " id=" + TenantAuthenticationProvider.mask(submitted));
  }
}
