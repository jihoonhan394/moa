package com.moara.moa.mail;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 발송 전 SSRF 가드: 내부/사설 대역 SMTP 호스트는 거부되어야 한다(네트워크 접속 이전에 차단). */
@SpringBootTest
@ActiveProfiles("test")
class MailServiceTest {
  @Autowired private MailService mailService;
  @Autowired private MailSettingService settingService;
  @Autowired private TenantService tenantService;

  @Test
  void rejectsInternalHostsBeforeConnecting() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("가드사", "GUARD" + System.nanoTime()));
    // 루프백/링크로컬(메타데이터)/사설망(RFC1918) 리터럴 IP는 발송기 구성 단계에서 거부된다.
    for (String internal : new String[] {"127.0.0.1", "169.254.169.254", "10.1.2.3", "192.168.0.5"}) {
      MailSetting setting = settingService.saveForTenant(tenant.getId(), new MailSettingForm(
          internal, 25, null, null, "no-reply@example.com", "가드", false, true));
      assertThrows(MailNotAllowedException.class, () -> mailService.sendTest(setting, "victim@example.com"));
    }
  }
}
