package com.moara.moa.mail;

import com.moara.moa.credential.SecretVault;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SMTP 설정 조회/저장. 스코프별(플랫폼=tenantId null, 기관=tenantId) 단일 행을 보장한다.
 * 비밀번호는 {@link SecretVault} 봉투암호화로만 저장하며 평문/해시를 남기지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class MailSettingService {
  private final MailSettingRepository repository;
  private final SecretVault secretVault;

  public MailSettingService(MailSettingRepository repository, SecretVault secretVault) {
    this.repository = repository;
    this.secretVault = secretVault;
  }

  /** 플랫폼 스코프 설정(없으면 empty). */
  public Optional<MailSetting> findPlatform() {
    return repository.findByTenantIdIsNull();
  }

  /** 기관 스코프 설정(없으면 empty). */
  public Optional<MailSetting> findForTenant(UUID tenantId) {
    return repository.findByTenantId(tenantId);
  }

  @Transactional
  public MailSetting savePlatform(MailSettingForm form) {
    return save(null, form);
  }

  @Transactional
  public MailSetting saveForTenant(UUID tenantId, MailSettingForm form) {
    return save(tenantId, form);
  }

  /**
   * 스코프 설정을 upsert한다. 비밀번호는 입력했을 때만 재암호화하여 교체한다.
   * 스코프별 단일 행은 DB 제약(기관: UNIQUE(tenant_id), 플랫폼: 고정 PK)으로 보장한다 —
   * 정상 흐름은 기존 행을 읽어 갱신하므로 제약에 걸리지 않고, 오직 동시 '최초 삽입' 경합에서만
   * 한쪽이 무결성 제약 위반(DataIntegrityViolationException)으로 실패한다. 이때도 중복 행은 만들어지지 않으므로
   * (핵심 위험인 영구 오류가 발생하지 않음) 해당 예외는 일시 오류로 상위에 전달한다.
   */
  private MailSetting save(UUID tenantId, MailSettingForm form) {
    OffsetDateTime now = OffsetDateTime.now();
    MailSetting setting = (tenantId == null ? repository.findByTenantIdIsNull()
        : repository.findByTenantId(tenantId))
        .orElseGet(() -> MailSetting.create(tenantId, now));
    setting.updateConfig(
        form.host(), form.port(), form.username(), form.fromAddress(), form.fromName(),
        form.starttls(), form.enabled(), now);
    if (form.password() != null && !form.password().isBlank()) {
      setting.setSecret(secretVault.encrypt(form.password(), tenantId), now);
    }
    return repository.saveAndFlush(setting);
  }

  /** 설정의 SMTP 비밀번호를 복호화한다. 비밀번호 미설정이면 null. */
  public String decryptPassword(MailSetting setting) {
    if (setting == null || !setting.hasSecret()) {
      return null;
    }
    return secretVault.decrypt(
        setting.getSecretCiphertext(), setting.getDekWrapped(), setting.getKeyVersion(), setting.getTenantId());
  }
}
