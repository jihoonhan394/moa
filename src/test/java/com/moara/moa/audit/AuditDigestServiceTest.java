package com.moara.moa.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 감사 기록 일 요약.
 *
 * <p>검증의 핵심은 <b>무엇이 이상한지는 코드가 판정한다</b>는 것이다. AI가 설정되지 않은
 * 테스트 환경에서도 "확인된 사실"은 그대로 나와야 한다 — AI는 문장으로 바꾸는 보조일 뿐이고,
 * 없다고 기능이 사라지면 안 된다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditDigestServiceTest {
  @Autowired private AuditDigestService digestService;
  @Autowired private AuditLogService auditLogService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void 기록이_없으면_빈_요약이다() {
    UUID tenantId = tenant();

    var digest = digestService.forDate(tenantId, LocalDate.now(AuditDigestService.ZONE));

    assertThat(digest.total()).isZero();
    assertThat(digest.facts()).isEmpty();
    assertThat(digest.summary()).isNull();
  }

  /** 실패가 한 사람에게 몰리면 사실로 잡는다 — 자격증명 대입 시도의 전형적인 모양이다. */
  @Test
  void 실패가_한_사람에게_몰리면_짚어_낸다() {
    UUID tenantId = tenant();
    ManagedUser actor = user(tenantId);
    for (int i = 0; i < 4; i++) {
      auditLogService.recordTenantAction(tenantId, actor.getId(), "CONNECTION_OPEN",
          "Asset", UUID.randomUUID(), AuditResult.FAILURE, "접속 실패");
    }

    var digest = today(tenantId);

    assertThat(digest.total()).isEqualTo(4);
    assertThat(digest.facts()).anyMatch(f -> f.contains("실패한 작업 4건"));
    assertThat(digest.facts()).anyMatch(f -> f.contains("몰림") && f.contains(actor.getName()));
  }

  /** 민감 행위는 한 건이어도 짚는다. PAM 제품에서 2FA 비활성화가 조용히 지나가면 안 된다. */
  @Test
  void 민감_행위는_한_건이어도_짚는다() {
    UUID tenantId = tenant();
    ManagedUser actor = user(tenantId);
    auditLogService.recordTenantAction(tenantId, actor.getId(), "USER_TWO_FACTOR_DISABLE",
        "ManagedUser", UUID.randomUUID(), AuditResult.SUCCESS, null);

    assertThat(today(tenantId).facts())
        .anyMatch(f -> f.contains("USER_TWO_FACTOR_DISABLE") && f.contains(actor.getName()));
  }

  /** 평범한 기록만 있으면 "특이 사항 없음"이라고 분명히 말한다 — 침묵은 장애와 구분되지 않는다. */
  @Test
  void 특이_사항이_없으면_없다고_말한다() {
    UUID tenantId = tenant();
    ManagedUser actor = user(tenantId);
    auditLogService.recordTenantAction(tenantId, actor.getId(), "INVENTORY_CREATE",
        "InventoryItem", UUID.randomUUID(), AuditResult.SUCCESS, null);

    var digest = today(tenantId);

    assertThat(digest.total()).isEqualTo(1);
    // 낮에 돌면 특이사항 없음, 야간에 돌면 야간 작업이 잡힌다 — 둘 중 하나는 반드시 나온다.
    assertThat(digest.facts()).isNotEmpty();
  }

  /** AI가 없으면 요약 문장만 비고 사실은 그대로다. */
  @Test
  void AI가_없어도_사실은_그대로_나온다() {
    UUID tenantId = tenant();
    ManagedUser actor = user(tenantId);
    auditLogService.recordTenantAction(tenantId, actor.getId(), "CREDENTIAL_REVEAL",
        "Credential", UUID.randomUUID(), AuditResult.SUCCESS, null);

    var digest = today(tenantId);

    assertThat(digest.summary()).isNull();       // 테스트 환경엔 AI 미설정
    assertThat(digest.facts()).isNotEmpty();     // 그래도 사실은 나온다
  }

  /** 기관 경계 — 남의 기관 기록이 섞이면 그 자체로 보안 사고다. */
  @Test
  void 다른_기관의_기록은_섞이지_않는다() {
    UUID a = tenant();
    UUID b = tenant();
    ManagedUser actorA = user(a);
    auditLogService.recordTenantAction(a, actorA.getId(), "USER_OFFBOARD",
        "ManagedUser", UUID.randomUUID(), AuditResult.SUCCESS, null);

    assertThat(today(a).total()).isEqualTo(1);
    assertThat(today(b).total()).isZero();
  }

  /** 다른 날 기록은 포함하지 않는다. */
  @Test
  void 지정한_날짜만_센다() {
    UUID tenantId = tenant();
    ManagedUser actor = user(tenantId);
    auditLogService.recordTenantAction(tenantId, actor.getId(), "INVENTORY_CREATE",
        "InventoryItem", UUID.randomUUID(), AuditResult.SUCCESS, null);

    LocalDate today = LocalDate.now(AuditDigestService.ZONE);
    assertThat(digestService.forDate(tenantId, today).total()).isEqualTo(1);
    assertThat(digestService.forDate(tenantId, today.minusDays(3)).total()).isZero();
  }

  private AuditDigestService.Digest today(UUID tenantId) {
    return digestService.forDate(tenantId, LocalDate.now(AuditDigestService.ZONE));
  }

  private UUID tenant() {
    String suffix = String.valueOf(System.nanoTime());
    return tenantService.createTenant(new CreateTenantCommand("감사기관" + suffix, "AD" + suffix)).getId();
  }

  private ManagedUser user(UUID tenantId) {
    String username = "aud" + System.nanoTime();
    return userService.create(tenantId, new UserForm(
        username, "행위자" + username.substring(3, 8), username + "@example.com",
        "safe-password-123", UserStatus.ACTIVE));
  }
}
