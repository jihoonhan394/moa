package com.moara.moa.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 감사 로그 화면이 <b>쌓여도 열린다</b>는 것을 붙들어 둔다.
 *
 * <p>⚠️ 전에는 기관의 감사 로그를 전부 읽어 한 페이지에 그렸다
 * ({@code findAllByTenantIdOrderByCreatedAtDesc} + 템플릿의 {@code th:each}).
 * 감사 로그는 특권 행위마다 한 줄 쌓이고 <b>지우지 않으므로</b> 1년이면 수만 건이 된다 —
 * 그때 그 화면은 느려지는 게 아니라 열리지 않는다.
 *
 * <p>행 수가 페이지 크기를 넘는 상황을 만들어 놓고, 화면이 그만큼만 그리는지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditPagingTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuditLogService auditLogService;
  @Autowired private ManagedUserService userService;
  @Autowired private TenantService tenantService;

  @Test
  @DisplayName("⚠️ 한 페이지 크기를 넘겨 쌓아도 그만큼만 꺼낸다")
  void 페이지_크기를_넘지_않는다() {
    UUID tenant = newTenant();
    UUID actor = newUser(tenant).getId();
    int rows = AuditLogService.PAGE_SIZE + 12;
    for (int index = 0; index < rows; index++) {
      auditLogService.recordTenantAction(tenant, actor, "GROUP_CREATE", "AccessGroup",
          UUID.randomUUID(), AuditResult.SUCCESS, "행 " + index);
    }

    var first = auditLogService.findByTenant(tenant, null, 0);
    assertThat(first.getContent()).hasSize(AuditLogService.PAGE_SIZE);
    assertThat(first.getTotalElements()).isGreaterThanOrEqualTo(rows);
    assertThat(first.getTotalPages()).isGreaterThan(1);

    var second = auditLogService.findByTenant(tenant, null, 1);
    assertThat(second.getContent()).isNotEmpty();
    // 같은 줄이 두 페이지에 겹쳐 나오면 "몇 번째"를 셀 수 없다.
    assertThat(second.getContent()).doesNotContainAnyElementsOf(first.getContent());
  }

  @Test
  @DisplayName("최신순이다 — 감사 로그를 여는 이유는 '요즘 무슨 일이 있었나'다")
  void 최신순으로_나온다() {
    UUID tenant = newTenant();
    UUID actor = newUser(tenant).getId();
    for (int index = 0; index < 3; index++) {
      auditLogService.recordTenantAction(tenant, actor, "GROUP_CREATE", "AccessGroup",
          UUID.randomUUID(), AuditResult.SUCCESS, "순서 " + index);
    }

    List<AuditLog> page = auditLogService.findByTenant(tenant, null, 0).getContent();

    assertThat(page).isSortedAccordingTo(
        (a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
  }

  @Test
  @DisplayName("기간 필터가 과거를 잘라낸다")
  void 기간_필터가_걸린다() {
    UUID tenant = newTenant();
    UUID actor = newUser(tenant).getId();
    auditLogService.recordTenantAction(tenant, actor, "GROUP_CREATE", "AccessGroup",
        UUID.randomUUID(), AuditResult.SUCCESS, "방금");

    // 1시간 전 이후 → 방금 넣은 것이 보인다
    assertThat(auditLogService.findByTenant(
        tenant, java.time.OffsetDateTime.now().minusHours(1), 0).getContent()).isNotEmpty();
    // 1시간 뒤 이후 → 아무것도 없다
    assertThat(auditLogService.findByTenant(
        tenant, java.time.OffsetDateTime.now().plusHours(1), 0).getContent()).isEmpty();
  }

  @Test
  @DisplayName("⚠️ 화면도 페이지만큼만 그린다 — 서비스만 고치고 템플릿이 전부 그리면 의미가 없다")
  void 화면이_페이지만큼만_그린다() throws Exception {
    UUID tenant = newTenant();
    ManagedUser admin = newUser(tenant);
    int rows = AuditLogService.PAGE_SIZE + 12;
    for (int index = 0; index < rows; index++) {
      auditLogService.recordTenantAction(tenant, admin.getId(), "GROUP_CREATE", "AccessGroup",
          UUID.randomUUID(), AuditResult.SUCCESS, "행 " + index);
    }

    String html = mockMvc.perform(get("/audit").with(authentication(auth(admin))))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    // 표 머리 1 + 본문 행들. 요약 패널에도 table-row 가 있으므로 상한만 본다.
    int tableRows = html.split("class=\"table-row\"", -1).length - 1;
    assertThat(tableRows)
        .as("쌓인 %d건을 전부 그리면 안 된다", rows)
        .isLessThan(rows);
    assertThat(html).contains("페이지");
  }

  private UUID newTenant() {
    return tenantService.createTenant(
        new CreateTenantCommand("감사사", "AD" + System.nanoTime())).getId();
  }

  private ManagedUser newUser(UUID tenantId) {
    String name = "aud" + System.nanoTime();
    return userService.create(tenantId, new UserForm(
        name, "감사담당", name + "@example.com", "safe-password-123", UserStatus.ACTIVE),
        Set.of(UserRole.TENANT_ADMIN));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    return new UsernamePasswordAuthenticationToken(new MoaUserDetails(user), "",
        List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
  }
}
