package com.moara.moa.remote;

import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원격 호스트 신원의 기록과 대조 — TOFU(trust on first use)의 판단이 사는 곳.
 *
 * <p>규칙은 두 줄이다. <b>처음 본 호스트는 기록하고 통과시킨다. 기록과 다르면 막는다.</b>
 * 첫 연결이 이미 가로채였다면 그 가짜를 기록하므로 완전한 보증은 아니다. 하지만 그 뒤로는
 * 조용한 중간자가 불가능해진다 — 한 번은 통해도 계속 통하지는 않는다. 호스트 키를 미리
 * 전부 등록받는 방식이 더 강하지만, 그때까지 모든 제어가 막혀 아무도 쓰지 않는다.
 */
@Service
public class RemoteHostKeyStore {
  private final RemoteHostKeyRepository repository;

  public RemoteHostKeyStore(RemoteHostKeyRepository repository) {
    this.repository = repository;
  }

  /** 대조 결과. */
  public enum Verdict {
    /** 처음 본 호스트 — 방금 기록했다. */
    RECORDED,
    /** 기록과 같다. */
    MATCHED,
    /** 기록과 다르다 — 연결하면 안 된다. */
    MISMATCHED
  }

  /**
   * 신원을 대조하고, 처음 보는 것이면 기록한다.
   *
   * <p><b>새 트랜잭션에서 쓴다.</b> 이 메서드는 제어 화면의 {@code @Transactional(readOnly = true)}
   * 안에서 불린다 — 읽기 전용 트랜잭션에 얹으면 Hibernate가 flush하지 않아 기록이 조용히
   * 사라지고, 매번 "처음 본 호스트"가 되어 검증이 아무 일도 하지 않게 된다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Verdict verify(
      UUID tenantId, String host, int port, RemoteChannel channel,
      String keyType, String fingerprint) {
    OffsetDateTime now = OffsetDateTime.now();
    Optional<RemoteHostKey> known =
        repository.findByTenantIdAndHostAndPortAndChannel(tenantId, host, port, channel);

    if (known.isEmpty()) {
      repository.save(
          new RemoteHostKey(tenantId, host, port, channel, keyType, fingerprint, now));
      return Verdict.RECORDED;
    }

    RemoteHostKey recorded = known.get();
    if (!recorded.getFingerprint().equals(fingerprint)) {
      return Verdict.MISMATCHED;
    }
    recorded.seenAgain(now);
    repository.save(recorded);
    return Verdict.MATCHED;
  }

  /**
   * 기록을 지운다. 서버를 다시 깔면 신원이 정당하게 바뀌므로, 사람이 확인한 뒤 지워 다음
   * 연결을 새 "처음"으로 만든다. 이 문이 없으면 재설치 한 번에 제어가 영영 막힌다.
   */
  @Transactional
  public void forget(UUID tenantId, String host, int port) {
    repository.deleteByTenantIdAndHostAndPort(tenantId, host, port);
  }

  @Transactional(readOnly = true)
  public List<RemoteHostKey> findByTenant(UUID tenantId) {
    return repository.findAllByTenantIdOrderByHostAsc(tenantId);
  }

  @Transactional(readOnly = true)
  public Optional<RemoteHostKey> find(
      UUID tenantId, String host, int port, RemoteChannel channel) {
    return repository.findByTenantIdAndHostAndPortAndChannel(tenantId, host, port, channel);
  }

  /** 사람이 눈으로 대조할 수 있게 OpenSSH와 같은 표기로 만든다: {@code SHA256:<base64>}. */
  public static String fingerprintOf(byte[] key) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(key);
      return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest);
    } catch (Exception impossible) {
      // SHA-256은 모든 JRE가 반드시 제공한다(JCA 표준).
      throw new IllegalStateException("SHA-256을 쓸 수 없습니다", impossible);
    }
  }

  /** 사람이 읽는 설명. 화면과 감사 기록에 그대로 쓴다. */
  public static String mismatchMessage(String host, int port, String seen, String recorded) {
    return "원격 호스트 신원이 기록과 다릅니다 — " + host + ":" + port
        + " (이번: " + seen + " / 기록: " + recorded + "). "
        + "서버를 다시 설치했다면 서버 상세에서 기록을 지운 뒤 다시 시도하세요. "
        + "그런 적이 없다면 중간자 공격일 수 있으므로 연결하지 마세요.";
  }

}
