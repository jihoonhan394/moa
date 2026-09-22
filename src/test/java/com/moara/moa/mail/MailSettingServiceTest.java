package com.moara.moa.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** SMTP 설정 저장/조회: 봉투암호화 왕복, 비밀번호 보존(빈 값 시), 스코프별 단일 행을 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
class MailSettingServiceTest {
  @Autowired private MailSettingService service;
  @Autowired private TenantService tenantService;

  @Test
  void savesTenantSettingAndRoundTripsPassword() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("메일사", "MAIL" + System.nanoTime()));
    MailSetting saved = service.saveForTenant(tenant.getId(), new MailSettingForm(
        "smtp.example.com", 587, "user@example.com", "s3cret-pass", "no-reply@example.com", "우리회사", true, true));

    assertTrue(saved.hasSecret());
    assertEquals("s3cret-pass", service.decryptPassword(saved));
    assertTrue(saved.isSendable());
  }

  @Test
  void blankPasswordKeepsExistingSecretAndSingleRowPerScope() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("메일사2", "MAIL" + System.nanoTime()));
    UUID id = tenant.getId();
    service.saveForTenant(id, new MailSettingForm(
        "smtp.example.com", 587, "u", "orig-pass", "from@example.com", "N", true, true));

    // 비밀번호를 비우고 다른 필드만 수정 → 기존 비밀번호 유지, 같은 행 갱신(단일 행).
    MailSetting updated = service.saveForTenant(id, new MailSettingForm(
        "smtp2.example.com", 25, "u", "", "from@example.com", "N", false, false));

    assertEquals("smtp2.example.com", updated.getHost());
    assertEquals("orig-pass", service.decryptPassword(updated));
    assertEquals(1L, tenantSettingCount(id));
  }

  @Test
  void platformScopeIsSeparateFromTenantScope() {
    service.savePlatform(new MailSettingForm(
        "smtp.platform", 587, "p", "plat-pass", "moa@example.com", "MOA", true, true));
    assertTrue(service.findPlatform().isPresent());
    assertNull(service.findPlatform().orElseThrow().getTenantId());

    // 임의 기관은 플랫폼 설정과 독립적이다.
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("독립사", "IND" + System.nanoTime()));
    assertFalse(service.findForTenant(tenant.getId()).isPresent());
  }

  private long tenantSettingCount(UUID tenantId) {
    return service.findForTenant(tenantId).isPresent() ? 1L : 0L;
  }
}
