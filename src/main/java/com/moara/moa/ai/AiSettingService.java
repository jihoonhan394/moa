package com.moara.moa.ai;

import com.moara.moa.credential.SecretVault;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기관별 AI 설정 저장/조회. API 키는 SecretVault로 봉투암호화한다(SMTP와 동일 패턴). */
@Service
@Transactional(readOnly = true)
public class AiSettingService {
  private final AiSettingRepository repository;
  private final SecretVault secretVault;

  public AiSettingService(AiSettingRepository repository, SecretVault secretVault) {
    this.repository = repository;
    this.secretVault = secretVault;
  }

  public Optional<AiSetting> findForTenant(UUID tenantId) {
    return repository.findByTenantId(tenantId);
  }

  @Transactional
  public AiSetting saveForTenant(UUID tenantId, AiSettingForm form) {
    OffsetDateTime now = OffsetDateTime.now();
    AiSetting setting = repository.findByTenantId(tenantId).orElseGet(() -> AiSetting.create(tenantId, now));
    setting.updateConfig(form.provider(), form.model(), form.baseUrl(), form.enabled(), now);
    // 키를 새로 입력했을 때만 재암호화(빈 값=기존 유지).
    if (form.apiKey() != null && !form.apiKey().isBlank()) {
      setting.setSecret(secretVault.encrypt(form.apiKey().trim(), tenantId), now);
    }
    return repository.saveAndFlush(setting);
  }

  /** 저장된 API 키 복호화. 미설정이면 null. */
  public String decryptApiKey(AiSetting setting) {
    if (setting == null || !setting.hasSecret()) {
      return null;
    }
    return secretVault.decrypt(
        setting.getSecretCiphertext(), setting.getDekWrapped(), setting.getKeyVersion(), setting.getTenantId());
  }
}
