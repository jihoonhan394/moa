package com.moara.moa.mail;

import com.moara.moa.credential.EncryptedSecret;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * SMTP 발송 설정. {@code tenantId}가 null이면 플랫폼(MOA) 스코프(→기관 관리자에게 발송),
 * 값이 있으면 해당 기관 스코프(→기관 사용자에게 발송)다. 스코프별 단일 행은 서비스가 보장한다.
 * SMTP 비밀번호는 평문으로 담지 않고 봉투암호화 결과만 보관한다.
 */
@Entity
@Table(name = "mail_settings")
public class MailSetting {
  /**
   * 플랫폼(tenant_id NULL) 스코프의 고정 PK. tenant_id NULL 행은 DB 유니크로 단일화할 수 없으므로
   * (SQL은 NULL을 서로 다르게 취급) 플랫폼 행은 이 고정 PK를 써서 동시 삽입이 기본키에서 충돌하도록 한다.
   */
  public static final UUID PLATFORM_ID = UUID.fromString("00000000-0000-0000-0000-000000000a11");

  @Id private UUID id;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(nullable = false, length = 255)
  private String host;

  @Column(nullable = false)
  private int port;

  @Column(length = 255)
  private String username;

  @Column(name = "secret_ciphertext", length = 4096)
  private String secretCiphertext;

  @Column(name = "dek_wrapped", length = 4096)
  private String dekWrapped;

  @Column(name = "key_version")
  private Integer keyVersion;

  @Column(name = "from_address", nullable = false, length = 255)
  private String fromAddress;

  @Column(name = "from_name", length = 100)
  private String fromName;

  @Column(nullable = false)
  private boolean starttls;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected MailSetting() {}

  public static MailSetting create(UUID tenantId, OffsetDateTime now) {
    MailSetting setting = new MailSetting();
    // 플랫폼 스코프는 고정 PK(동시 삽입이 PK 충돌로 걸러짐), 기관 스코프는 임의 PK + UNIQUE(tenant_id)로 단일화.
    setting.id = tenantId == null ? PLATFORM_ID : UUID.randomUUID();
    setting.tenantId = tenantId;
    setting.port = 587;
    setting.starttls = true;
    setting.enabled = false;
    setting.updatedAt = now;
    return setting;
  }

  public void updateConfig(
      String host, int port, String username, String fromAddress, String fromName,
      boolean starttls, boolean enabled, OffsetDateTime now) {
    this.host = host == null ? null : host.trim();
    this.port = port;
    this.username = username == null || username.isBlank() ? null : username.trim();
    this.fromAddress = fromAddress == null ? null : fromAddress.trim();
    this.fromName = fromName == null || fromName.isBlank() ? null : fromName.trim();
    this.starttls = starttls;
    this.enabled = enabled;
    this.updatedAt = now;
  }

  /** SMTP 비밀번호(봉투암호화 결과)를 교체한다. null이면 비밀번호 없음(인증 없이 전송)으로 둔다. */
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

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public String getHost() { return host; }
  public int getPort() { return port; }
  public String getUsername() { return username; }
  public String getSecretCiphertext() { return secretCiphertext; }
  public String getDekWrapped() { return dekWrapped; }
  public Integer getKeyVersion() { return keyVersion; }
  public String getFromAddress() { return fromAddress; }
  public String getFromName() { return fromName; }
  public boolean isStarttls() { return starttls; }
  public boolean isEnabled() { return enabled; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }

  /** 설정이 실제로 발송 가능한 상태인지(활성 + 호스트/발신주소 존재). */
  public boolean isSendable() {
    return enabled && host != null && !host.isBlank() && fromAddress != null && !fromAddress.isBlank();
  }
}
