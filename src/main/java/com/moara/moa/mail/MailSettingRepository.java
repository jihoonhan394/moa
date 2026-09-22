package com.moara.moa.mail;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailSettingRepository extends JpaRepository<MailSetting, UUID> {
  /** 기관 스코프 설정. */
  Optional<MailSetting> findByTenantId(UUID tenantId);

  /** 플랫폼(MOA) 스코프 설정(tenant_id IS NULL). */
  Optional<MailSetting> findByTenantIdIsNull();
}
