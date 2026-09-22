package com.moara.moa.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MoaUserDetailsServiceTest {
  @Autowired private ManagedUserService userService;
  @Autowired private MoaUserDetailsService userDetailsService;

  @Test
  void bootstrapsSystemAdminAndLoadsPrincipalContext() {
    String username = "admin" + System.nanoTime();
    ManagedUser admin = userService.ensureSystemAdmin(username, "safe-password-123");

    // SYSTEM_ADMIN은 특정 테넌트에 속하지 않고 email을 요구하지 않는다.
    assertTrue(admin.hasRole(UserRole.SYSTEM_ADMIN));
    assertNull(admin.getTenantId());
    assertNull(admin.getEmail());

    MoaUserDetails details = (MoaUserDetails) userDetailsService.loadUserByUsername(username);
    assertEquals(admin.getId(), details.getUserId());
    assertTrue(details.hasRole(UserRole.SYSTEM_ADMIN));
    assertNull(details.getTenantId());
    assertTrue(details.isEnabled());
    assertTrue(details.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_SYSTEM_ADMIN")));
  }

  @Test
  void ensureSystemAdminIsIdempotent() {
    String username = "admin" + System.nanoTime();
    ManagedUser first = userService.ensureSystemAdmin(username, "safe-password-123");
    ManagedUser second = userService.ensureSystemAdmin(username, "safe-password-456");

    // 동일 계정을 재사용하고 역할을 유지한다(중복 생성 없음).
    assertEquals(first.getId(), second.getId());
    assertTrue(second.hasRole(UserRole.SYSTEM_ADMIN));
  }

  @Test
  void disabledUserIsNotEnabled() {
    String username = "user" + System.nanoTime();
    ManagedUser created = userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
    userService.disable(created.getId());

    UserDetails details = userDetailsService.loadUserByUsername(username);
    assertFalse(details.isEnabled());
  }

  @Test
  void unknownUsernameThrows() {
    assertThrows(UsernameNotFoundException.class,
        () -> userDetailsService.loadUserByUsername("no-such-user-" + System.nanoTime()));
  }
}
