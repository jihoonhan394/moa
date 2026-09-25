package com.moara.moa.remote;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 원격 호스트의 신원 기록 한 줄 — "이 주소에 답하는 상대는 이 지문을 갖고 있었다".
 *
 * <p>처음 붙을 때 기록하고, 그 뒤로는 이것과 비교한다. 지문이 달라지면 서버를 다시 깔았거나
 * <b>중간에 누가 끼어든 것</b>이다. 둘을 코드가 구별할 방법은 없으므로 연결을 막고 사람에게
 * 넘긴다 — 관리자가 {@link RemoteHostKeyStore#forget}으로 지워야 다시 붙는다.
 *
 * <p>키 원문이 아니라 지문만 둔다. 비교에는 지문이면 충분하고, 원문을 갖고 있으면 그것을
 * 지켜야 할 대상이 하나 더 늘 뿐이다.
 */
@Entity
@Table(name = "remote_host_keys")
public class RemoteHostKey {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String host;

  @Column(nullable = false)
  private int port;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private RemoteChannel channel;

  @Column(name = "key_type", nullable = false)
  private String keyType;

  @Column(nullable = false, length = 128)
  private String fingerprint;

  @Column(name = "first_seen_at", nullable = false)
  private OffsetDateTime firstSeenAt;

  @Column(name = "last_seen_at", nullable = false)
  private OffsetDateTime lastSeenAt;

  protected RemoteHostKey() {}

  RemoteHostKey(UUID tenantId, String host, int port, RemoteChannel channel,
      String keyType, String fingerprint, OffsetDateTime seenAt) {
    this.id = UUID.randomUUID();
    this.tenantId = tenantId;
    this.host = host;
    this.port = port;
    this.channel = channel;
    this.keyType = keyType;
    this.fingerprint = fingerprint;
    this.firstSeenAt = seenAt;
    this.lastSeenAt = seenAt;
  }

  /** 같은 지문을 또 봤다. 언제 마지막으로 확인됐는지만 갱신한다. */
  void seenAgain(OffsetDateTime at) {
    this.lastSeenAt = at;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public RemoteChannel getChannel() {
    return channel;
  }

  public String getKeyType() {
    return keyType;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public OffsetDateTime getFirstSeenAt() {
    return firstSeenAt;
  }

  public OffsetDateTime getLastSeenAt() {
    return lastSeenAt;
  }
}
