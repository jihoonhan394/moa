package com.moara.moa.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 실패한 로그인 시도 한 건. <b>잠금 판단에만</b> 쓰는 운영 상태이며, 영구 기록은 감사 로그가 맡는다.
 *
 * <p>성공하면 그 계정의 행을 지우고, 창(15분)이 지난 행도 지운다 — 평소에는 거의 비어 있어야
 * 정상이다. 이 표가 커져 있다면 그 자체가 신호다.
 */
@Entity
@Table(name = "login_failures")
public class LoginFailure {
  /**
   * 플랫폼(SYSTEM_ADMIN) 로그인의 기관 자리. 소속 기관이 없지만 NULL로 두면
   * {@code tenant_id = ?} 조회가 영영 일치하지 않아 <b>플랫폼 계정만 잠기지 않는다</b>.
   */
  public static final UUID PLATFORM_SCOPE = new UUID(0L, 0L);

  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String username;

  @Column(name = "client_ip", nullable = false, length = 64)
  private String clientIp;

  @Column(name = "attempted_at", nullable = false)
  private OffsetDateTime attemptedAt;

  protected LoginFailure() {}

  LoginFailure(UUID tenantId, String username, String clientIp, OffsetDateTime attemptedAt) {
    this.id = UUID.randomUUID();
    this.tenantId = tenantId;
    this.username = username;
    this.clientIp = clientIp;
    this.attemptedAt = attemptedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getUsername() {
    return username;
  }

  public String getClientIp() {
    return clientIp;
  }

  public OffsetDateTime getAttemptedAt() {
    return attemptedAt;
  }
}
