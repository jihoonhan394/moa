package com.moara.moa.credential;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 저장된 자격증명(솔루션 계정). 비밀은 봉투암호화된 상태로만 보관한다.
 * 평문 접근자는 없다(복호화는 {@link SecretVault} 경유). 암호문/래핑DEK는 복호화를 위해 노출한다.
 */
@Entity
@Table(name = "credentials")
public class Credential {
  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CredentialType type;

  @Column(nullable = false, length = 255)
  private String username;

  @Column(name = "secret_ciphertext", nullable = false)
  private String secretCiphertext;

  @Column(name = "dek_wrapped", nullable = false)
  private String dekWrapped;

  @Column(name = "key_version", nullable = false)
  private int keyVersion;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** 설정법 등 연동 위키 공간(공유기 설정·SMTP 설정법 문서). */
  @Column(name = "wiki_space_id")
  private UUID wikiSpaceId;

  /**
   * 이 계정으로 들어가는 관리 페이지 주소(공유기 설정, 프린터 콘솔 등). 비워 둘 수 있다.
   * 주소와 로그인 정보가 한곳에 있어야 공유받은 사람이 바로 쓸 수 있다.
   */
  @Column(length = 500)
  private String url;

  protected Credential() {}

  public Credential(
      UUID id, UUID tenantId, String name, CredentialType type, String username,
      EncryptedSecret secret, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.createdAt = now;
    applyMeta(name, type, username, null, now);
    replaceSecret(secret, now);
  }

  /** 메타데이터(이름/유형/계정/주소) 갱신. 비밀은 건드리지 않는다. */
  public void applyMeta(
      String name, CredentialType type, String username, String url, OffsetDateTime now) {
    this.name = name.trim();
    this.type = type;
    this.username = username.trim();
    this.url = normalizeUrl(url);
    this.updatedAt = now;
  }

  /**
   * 주소를 다듬는다. 스킴이 없으면 {@code https://}를 붙이고, <b>http(s)가 아닌 스킴은 버린다</b>
   * — {@code javascript:} 같은 값이 링크로 렌더되면 클릭 한 번으로 스크립트가 도는 통로가 된다
   * (본문은 새니타이즈하면서 링크로 뚫리는 일을 막는다).
   */
  private static String normalizeUrl(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String trimmed = raw.trim();
    String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
    if (lower.startsWith("http://") || lower.startsWith("https://")) {
      return trimmed;
    }
    if (lower.contains("://") || lower.contains(":")) {
      return null; // 알 수 없는 스킴은 버린다
    }
    return "https://" + trimmed;
  }

  /** 비밀 교체(재암호화 결과로). */
  public void replaceSecret(EncryptedSecret secret, OffsetDateTime now) {
    this.secretCiphertext = secret.secretCiphertext();
    this.dekWrapped = secret.dekWrapped();
    this.keyVersion = secret.keyVersion();
    this.updatedAt = now;
  }

  /** 설정법 위키 공간 연동(없애려면 null). */
  public void linkWiki(UUID wikiSpaceId, OffsetDateTime now) {
    this.wikiSpaceId = wikiSpaceId;
    this.updatedAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getWikiSpaceId() { return wikiSpaceId; }
  public String getUrl() { return url; }
  public String getName() { return name; }
  public CredentialType getType() { return type; }
  public String getUsername() { return username; }
  public String getSecretCiphertext() { return secretCiphertext; }
  public String getDekWrapped() { return dekWrapped; }
  public int getKeyVersion() { return keyVersion; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
