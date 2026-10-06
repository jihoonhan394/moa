package com.moara.moa.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 신원의 유일 범위는 <b>기관</b>이다(V16 — 두레이식 2단계 진입). 같은 사람이 두 기관에 속할 수
 * 있어야 한다 — 외주 담당자·업체 엔지니어가 현실적으로 그렇다.
 *
 * <p>⚠️ 이 테스트가 없던 동안 H2 에는 V2 의 <b>전역</b> 이메일 UNIQUE 가 남아 있었고
 * (V16 이 PostgreSQL 자동 이름으로만 지워서 H2 에서는 no-op), 서비스는 기관별로만 검사하므로
 * 두 기관에 같은 이메일을 넣으면 <b>whitelabel 500</b> 이 나갔다. V77 이 그 제약을 없앴다.
 *
 * <p>운영(PostgreSQL)에는 제약이 없었으므로 <b>로컬·테스트에서만</b> 나는 버그였다 — 그래서
 * 스키마가 갈라졌는지를 테스트가 지켜야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class CrossTenantIdentityTest {

  @Autowired private ManagedUserService userService;
  @Autowired private TenantService tenantService;

  @Test
  @DisplayName("⚠️ 같은 이메일을 두 기관에 둘 수 있다 — 한 사람이 두 기관의 담당일 수 있다")
  void 같은_이메일이_두_기관에_공존한다() {
    String email = "shared" + System.nanoTime() + "@example.com";
    UUID other = newTenant();

    ManagedUser mine = userService.create(new UserForm(
        "u" + System.nanoTime(), "공용담당", email, "safe-password-123", UserStatus.ACTIVE));
    ManagedUser theirs = userService.createTenantAdmin(
        other, "u" + System.nanoTime(), "공용담당", email, "010-1234-5678", "safe-password-123");

    assertThat(mine.getTenantId()).isEqualTo(Tenant.DEFAULT_TENANT_ID);
    assertThat(theirs.getTenantId()).isEqualTo(other);
    assertThat(theirs.getEmail()).isEqualTo(email);
  }

  @Test
  @DisplayName("한 기관 안에서는 이메일이 유일하다 — 로그인 키다. 500 이 아니라 거부다")
  void 같은_기관_안에서는_거부한다() {
    String email = "dup" + System.nanoTime() + "@example.com";
    UUID tenant = newTenant();
    userService.createTenantAdmin(
        tenant, "first", "첫사람", email, "010-1111-1111", "safe-password-123");

    assertThatThrownBy(() -> userService.createTenantAdmin(
        tenant, "second", "둘째", email, "010-2222-2222", "safe-password-123"))
        .isInstanceOf(DuplicateManagedUserException.class);
  }

  @Test
  @DisplayName("대소문자가 달라도 같은 기관 안에서는 같은 이메일로 본다")
  void 대소문자는_같은_이메일이다() {
    String email = "Case" + System.nanoTime() + "@Example.com";
    UUID tenant = newTenant();
    userService.createTenantAdmin(
        tenant, "first", "첫사람", email, "010-1111-1111", "safe-password-123");

    assertThatThrownBy(() -> userService.createTenantAdmin(
        tenant, "second", "둘째", email.toLowerCase(), "010-2222-2222", "safe-password-123"))
        .isInstanceOf(DuplicateManagedUserException.class);
  }

  @Test
  @DisplayName("⚠️ 세 기관까지도 공존한다 — 전역 제약이 남아 있으면 두 번째에서 이미 터진다")
  void 세_기관에도_공존한다() {
    String email = "triple" + System.nanoTime() + "@example.com";
    UUID a = newTenant();
    UUID b = newTenant();
    UUID c = newTenant();

    for (UUID tenant : new UUID[] {a, b, c}) {
      userService.createTenantAdmin(
          tenant, "u" + System.nanoTime(), "삼중담당", email, "010-3333-3333", "safe-password-123");
    }

    assertThat(userService.findByTenant(a)).anyMatch(u -> email.equals(u.getEmail()));
    assertThat(userService.findByTenant(b)).anyMatch(u -> email.equals(u.getEmail()));
    assertThat(userService.findByTenant(c)).anyMatch(u -> email.equals(u.getEmail()));
  }

  private UUID newTenant() {
    return tenantService.createTenant(
        new CreateTenantCommand("교차사", "XT" + System.nanoTime())).getId();
  }
}
