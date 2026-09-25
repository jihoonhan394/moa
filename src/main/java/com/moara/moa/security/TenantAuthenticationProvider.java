package com.moara.moa.security;

import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 기관(테넌트) 컨텍스트를 반영한 인증. 진입 1단계에서 세션에 담긴 기관으로 (기관+아이디)를 조회하고,
 * 플랫폼 진입이면 테넌트 없는 SYSTEM_ADMIN 풀에서 조회한다. <b>아이디만으로는 인증하지 않는다.</b>
 *
 * <p>이 빈이 존재하면 Spring Security 기본 DaoAuthenticationProvider 자동구성이 물러나므로,
 * 아이디 전역 조회로 인한 교차테넌트 인증이 발생하지 않는다.
 */
@Component
public class TenantAuthenticationProvider implements AuthenticationProvider {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(TenantAuthenticationProvider.class);
  private final MoaUserDetailsService userDetailsService;
  private final PasswordEncoder passwordEncoder;
  private final TenantRepository tenantRepository;
  private final LoginThrottle throttle;

  public TenantAuthenticationProvider(
      MoaUserDetailsService userDetailsService,
      PasswordEncoder passwordEncoder,
      TenantRepository tenantRepository,
      LoginThrottle throttle) {
    this.userDetailsService = userDetailsService;
    this.passwordEncoder = passwordEncoder;
    this.tenantRepository = tenantRepository;
    this.throttle = throttle;
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    String username = authentication.getName();
    String presented =
        authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();

    UUID tenantId = null;
    boolean platform = false;
    String clientIp = "unknown";
    if (authentication.getDetails() instanceof TenantAuthenticationDetails details) {
      tenantId = details.getTenantId();
      platform = details.isPlatform();
      clientIp = details.getClientIp();
    }
    if (!platform && tenantId == null) {
      // 기관 선택 없이 로그인 시도 → 진입 단계를 거치지 않은 요청. 인증 거부.
      log.warn("[LOGIN] no tenant selected: user={}", mask(username));
      throw new BadCredentialsException("No tenant selected");
    }

    // 비밀번호를 대조하기 전에 본다. 잠긴 뒤에도 매번 대조하면 느린 해시(BCrypt)를 계속
    // 돌리게 되어, 잠금이 오히려 공격자가 서버를 괴롭히는 도구가 된다.
    throttle.checkNotLocked(tenantId, username, clientIp);

    MoaUserDetails user;
    try {
      user = userDetailsService.loadUserByTenantAndUsername(tenantId, username, platform);
    } catch (UsernameNotFoundException exception) {
      log.warn("[LOGIN] user not found: tenant={} user={} platform={}", tenantId, mask(username), platform);
      throw new BadCredentialsException("Bad credentials");
    }
    if (!passwordEncoder.matches(presented, user.getPassword())) {
      log.warn("[LOGIN] bad password: tenant={} user={}", tenantId, mask(username));
      throw new BadCredentialsException("Bad credentials");
    }
    if (!user.isEnabled()) {
      log.warn("[LOGIN] disabled(계정 비활성/미승인): tenant={} user={}", tenantId, mask(username));
      throw new DisabledException("User is disabled");
    }
    // 기관 사용자는 소속 기관의 구독/활성 상태를 통과해야 로그인된다(만기/정지 차단).
    if (user.getTenantId() != null) {
      Tenant tenant = tenantRepository.findById(user.getTenantId()).orElse(null);
      if (tenant == null || !tenant.isAccessible(LocalDate.now())) {
        log.warn("[LOGIN] tenant not accessible: tenant={} user={}", user.getTenantId(), mask(username));
        throw new LockedException("기관 구독이 만료되었거나 비활성 상태입니다.");
      }
    }
    // 여기까지 왔으면 이 계정의 지난 실패는 없던 일이 된다. 출발지 카운터는 남긴다 —
    // 지우면 공격자가 자기 계정에 한 번 로그인하는 것만으로 자기 IP 기록을 씻을 수 있다.
    throttle.clearAccount(tenantId, username);

    UsernamePasswordAuthenticationToken result =
        UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    result.setDetails(authentication.getDetails());
    return result;
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }

  /**
   * 로그(운영 nohup 등)에 남는 로그인 식별자의 개인정보를 최소화한다. 이메일이면 앞 2글자만 노출하고
   * 로컬파트를 마스킹(ab***@domain), 그 외는 앞 2글자만 남긴다. (감사 추적은 audit_logs가 담당.)
   */
  static String mask(String principal) {
    if (principal == null || principal.isBlank()) {
      return "(none)";
    }
    String p = principal.trim();
    int at = p.indexOf('@');
    if (at > 0) {
      String local = p.substring(0, at);
      String head = local.length() <= 2 ? local : local.substring(0, 2);
      return head + "***" + p.substring(at);
    }
    return (p.length() <= 2 ? p : p.substring(0, 2)) + "***";
  }
}
