package com.moara.moa.expiration;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 만료 영향분석의 '사실 수집'을 AI 호출 없이 검증한다(AI 초안 자체는 외부 호출이라 여기서 제외).
 * "누가 영향받는가"를 계산하는 로직이 핵심.
 */
@SpringBootTest
@ActiveProfiles("test")
class ExpirationImpactServiceTest {
  @Autowired private ExpirationImpactService impactService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void accessGrantContextNamesTheAffectedUser() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("만료사", "EX" + System.nanoTime()));
    ManagedUser user = newUser(tenant.getId(), "홍길동");

    var context = impactService.buildContext(
        tenant.getId(), ExpirationSourceType.ACCESS_GRANT, user.getId(), "DB 접근 권한", null);

    assertThat(context.facts()).anyMatch(f -> f.contains("홍길동"));
    assertThat(context.facts()).anyMatch(f -> f.contains("접근 권한을 잃"));
  }

  @Test
  void subscriptionContextCoversWholeTenant() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("구독사", "ES" + System.nanoTime()));
    newUser(tenant.getId(), "사용자");

    var context = impactService.buildContext(
        tenant.getId(), ExpirationSourceType.SUBSCRIPTION, tenant.getId(), "구독", null);

    assertThat(context.facts()).anyMatch(f -> f.contains("기관 전체"));
  }

  private ManagedUser newUser(UUID tenantId, String name) {
    String username = "u" + System.nanoTime();
    return userService.create(tenantId,
        new UserForm(username, name, username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
