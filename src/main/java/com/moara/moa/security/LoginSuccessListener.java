package com.moara.moa.security;

import com.moara.moa.user.ManagedUserRepository;
import java.time.OffsetDateTime;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 성공 시 {@code last_login_at}을 기록한다. 인증 성공 이후에만 동작하므로
 * 실패한 로그인 시도는 기록되지 않는다.
 */
@Component
public class LoginSuccessListener {
  private final ManagedUserRepository userRepository;

  public LoginSuccessListener(ManagedUserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @EventListener
  @Transactional
  public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
    if (event.getAuthentication().getPrincipal() instanceof MoaUserDetails details) {
      userRepository.findById(details.getUserId())
          .ifPresent(user -> user.recordLogin(OffsetDateTime.now()));
    }
  }
}
