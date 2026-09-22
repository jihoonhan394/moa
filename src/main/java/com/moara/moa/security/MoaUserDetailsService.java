package com.moara.moa.security;

import com.moara.moa.user.ManagedUserRepository;
import java.util.Optional;
import java.util.UUID;
import com.moara.moa.user.ManagedUser;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code managed_users} 테이블을 인증 소스로 사용하는 UserDetailsService.
 * in-memory 관리자 계정을 대체하며, 로그인 시 사용자의 테넌트/역할을 principal에 실는다.
 */
@Service
public class MoaUserDetailsService implements UserDetailsService {
  private final ManagedUserRepository userRepository;

  public MoaUserDetailsService(ManagedUserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) {
    return userRepository.findByUsernameIgnoreCase(username)
        .map(MoaUserDetails::new)
        .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
  }

  /**
   * 로그인 조회. 기관 사용자는 <b>이메일</b>이 기관 내 유일 키(로그인 ID)이므로 (기관 + 이메일)로 조회한다.
   * {@code platform=true}면 테넌트가 없는(SYSTEM_ADMIN) 플랫폼 계정 풀에서 아이디(username)로 조회한다.
   * (username은 표시용 이름이라 로그인 키가 아니다 — 플랫폼 운영자만 아이디 로그인 유지.)
   */
  @Transactional(readOnly = true)
  public MoaUserDetails loadUserByTenantAndUsername(UUID tenantId, String principal, boolean platform) {
    Optional<ManagedUser> user =
        platform
            ? userRepository.findByTenantIdIsNullAndUsernameIgnoreCase(principal)
            : userRepository.findByTenantIdAndEmailIgnoreCase(tenantId, principal);
    return user.map(MoaUserDetails::new)
        .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + principal));
  }
}
