package com.moara.moa.security;

import com.moara.moa.credential.EncryptedSecret;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 사용자 2단계 인증(TOTP) 시크릿. 시크릿은 봉투암호화로만 저장. enabled=확정 등록 여부. */
@Entity
@Table(name = "user_totp")
public class UserTotp {
  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "secret_ciphertext", length = 4096)
  private String secretCiphertext;

  @Column(name = "dek_wrapped", length = 4096)
  private String dekWrapped;

  @Column(name = "key_version")
  private int keyVersion;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "confirmed_at")
  private OffsetDateTime confirmedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected UserTotp() {}

  public UserTotp(UUID userId, UUID tenantId, EncryptedSecret secret, OffsetDateTime now) {
    this.userId = userId;
    this.tenantId = tenantId;
    setSecret(secret);
    this.enabled = false;
    this.createdAt = now;
  }

  public void setSecret(EncryptedSecret secret) {
    this.secretCiphertext = secret.secretCiphertext();
    this.dekWrapped = secret.dekWrapped();
    this.keyVersion = secret.keyVersion();
  }

  /** 코드 확인 성공 시 활성화(확정). */
  public void enable(OffsetDateTime now) {
    this.enabled = true;
    this.confirmedAt = now;
  }

  public void disable() {
    this.enabled = false;
  }

  public UUID getUserId() { return userId; }
  public UUID getTenantId() { return tenantId; }
  public String getSecretCiphertext() { return secretCiphertext; }
  public String getDekWrapped() { return dekWrapped; }
  public int getKeyVersion() { return keyVersion; }
  public boolean isEnabled() { return enabled; }
  public OffsetDateTime getConfirmedAt() { return confirmedAt; }
}
