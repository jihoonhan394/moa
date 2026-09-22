package com.moara.moa.security;

import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 인증된 사용자의 principal. 사용자 식별자와 함께 <b>테넌트/역할 컨텍스트</b>를 담아
 * 이후 Service 계층의 테넌트 격리·권한 검사에서 참조한다.
 * SYSTEM_ADMIN은 특정 테넌트에 속하지 않으므로 {@code tenantId}가 null일 수 있다.
 */
public class MoaUserDetails implements UserDetails {
  private final UUID userId;
  private final UUID tenantId;
  private final Set<UserRole> roles;
  private final String username;
  private final String passwordHash;
  private final boolean enabled;

  public MoaUserDetails(ManagedUser user) {
    this.userId = user.getId();
    this.tenantId = user.getTenantId();
    this.roles = Set.copyOf(user.getRoles());
    this.username = user.getUsername();
    this.passwordHash = user.getPasswordHash();
    this.enabled = user.getStatus() == UserStatus.ACTIVE;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public Set<UserRole> getRoles() {
    return roles;
  }

  public boolean hasRole(UserRole role) {
    return roles.contains(role);
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return roles.stream()
        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
        .collect(Collectors.toSet());
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }
}
