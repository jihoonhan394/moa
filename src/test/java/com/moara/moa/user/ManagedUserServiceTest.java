package com.moara.moa.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moara.moa.tenant.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ManagedUserServiceTest {
  @Autowired private ManagedUserService userService;

  @Test
  void createsUpdatesAndDisablesUser() {
    String username = "user" + System.nanoTime();
    ManagedUser created = userService.create(new UserForm(
        username, "테스트 사용자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));

    ManagedUser updated = userService.update(Tenant.DEFAULT_TENANT_ID, created.getId(), new UserForm(
        username, "수정 사용자", username + "@example.com", "", UserStatus.ACTIVE));
    assertEquals("수정 사용자", updated.getName());

    userService.disable(Tenant.DEFAULT_TENANT_ID, created.getId());
    assertEquals(UserStatus.DISABLED, userService.findById(created.getId()).getStatus());
  }

  @Test
  void newUserBelongsToDefaultTenantAsUser() {
    String username = "user" + System.nanoTime();
    ManagedUser created = userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));

    assertEquals(Tenant.DEFAULT_TENANT_ID, created.getTenantId());
    assertEquals(java.util.Set.of(UserRole.USER), created.getRoles());
  }
}
