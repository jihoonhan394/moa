package com.moara.moa.ai;

import com.moara.moa.credential.EncryptedSecret;
import com.moara.moa.support.Values;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 기관별 AI 설정. API 키는 봉투암호화(SecretVault)로 저장 — 평문 컬럼 없음. 기관당 1행. */
@Entity
@Table(name = "ai_settings")
public class AiSetting {
  @Id
  private UUID id;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Enumerated(EnumType.STRING)
  private AiProvider provider;

  private String model;

  @Column(name = "base_url")
  private String baseUrl;

  @Column(name = "secret_ciphertext", length = 4096)
  private String secretCiphertext;

  @Column(name = "dek_wrapped", length = 4096)
  private String dekWrapped;

  @Column(name = "key_version")
  private Integer keyVersion;

  private boolean enabled;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  protected AiSetting() {}

  public static AiSetting create(UUID tenantId, OffsetDateTime now) {
    AiSetting setting = new AiSetting();
    setting.id = UUID.randomUUID();
    setting.tenantId = tenantId;
    setting.provider = AiProvider.DEEPSEEK;
    setting.enabled = false;
    setting.updatedAt = now;
    return setting;
  }

  public void updateConfig(AiProvider provider, String model, String baseUrl, boolean enabled, OffsetDateTime now) {
    this.provider = provider;
    this.model = blankToNull(model);
    this.baseUrl = blankToNull(baseUrl);
    this.enabled = enabled;
    this.updatedAt = now;
  }

  /** API 키 저장(null이면 지움). */
  public void setSecret(EncryptedSecret secret, OffsetDateTime now) {
    if (secret == null) {
      this.secretCiphertext = null;
      this.dekWrapped = null;
      this.keyVersion = null;
    } else {
      this.secretCiphertext = secret.secretCiphertext();
      this.dekWrapped = secret.dekWrapped();
      this.keyVersion = secret.keyVersion();
    }
    this.updatedAt = now;
  }

  public boolean hasSecret() {
    return secretCiphertext != null && dekWrapped != null && keyVersion != null;
  }

  /** 실제 사용할 모델/베이스URL(미설정이면 제공자 기본값). */
  public String effectiveModel() {
    return model != null ? model : provider.getDefaultModel();
  }

  public String effectiveBaseUrl() {
    return baseUrl != null ? baseUrl : provider.getDefaultBaseUrl();
  }

  private static String blankToNull(String value) {
    return Values.blankToNull(value);
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public AiProvider getProvider() { return provider; }
  public String getModel() { return model; }
  public String getBaseUrl() { return baseUrl; }
  public String getSecretCiphertext() { return secretCiphertext; }
  public String getDekWrapped() { return dekWrapped; }
  public Integer getKeyVersion() { return keyVersion; }
  public boolean isEnabled() { return enabled; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
