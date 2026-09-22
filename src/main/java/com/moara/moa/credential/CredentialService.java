package com.moara.moa.credential;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자격증명(솔루션 계정) 관리. 비밀은 저장 시 {@link SecretVault}로 봉투암호화하며, 평문은 반환·노출하지 않는다.
 * 제어 엔진이 실제 접속에 쓸 때만 {@link #resolveSecret}로 복호화한다(호출부에서 감사·비로깅 준수).
 * 모든 연산은 테넌트 스코프.
 */
@Service
@Transactional(readOnly = true)
public class CredentialService {
  private final CredentialRepository credentialRepository;
  private final SecretVault secretVault;
  private final Clock clock;

  @Autowired
  public CredentialService(CredentialRepository credentialRepository, SecretVault secretVault) {
    this(credentialRepository, secretVault, Clock.systemUTC());
  }

  CredentialService(CredentialRepository credentialRepository, SecretVault secretVault, Clock clock) {
    this.credentialRepository = credentialRepository;
    this.secretVault = secretVault;
    this.clock = clock;
  }

  @Transactional
  public Credential create(UUID tenantId, CredentialForm form) {
    if (form.secret() == null || form.secret().isBlank()) {
      throw new IllegalArgumentException("비밀(secret)은 필수입니다.");
    }
    credentialRepository.findByTenantIdAndName(tenantId, form.name().trim()).ifPresent(existing -> {
      throw new DuplicateCredentialException(form.name());
    });
    OffsetDateTime now = OffsetDateTime.now(clock);
    Credential credential = new Credential(
        UUID.randomUUID(), tenantId, form.name(), form.type(), form.username(),
        secretVault.encrypt(form.secret(), tenantId), now);
    return credentialRepository.save(credential);
  }

  public Credential findById(UUID tenantId, UUID id) {
    return credentialRepository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new CredentialNotFoundException(id));
  }

  public List<Credential> findAll(UUID tenantId) {
    return credentialRepository.findAllByTenantIdOrderByNameAsc(tenantId);
  }

  /** 메타데이터 수정 + (secret이 있으면) 비밀 교체. secret이 비면 기존 비밀 유지. */
  @Transactional
  public Credential update(UUID tenantId, UUID id, CredentialForm form) {
    Credential credential = findById(tenantId, id);
    credentialRepository.findByTenantIdAndName(tenantId, form.name().trim())
        .filter(other -> !other.getId().equals(id))
        .ifPresent(other -> {
          throw new DuplicateCredentialException(form.name());
        });
    OffsetDateTime now = OffsetDateTime.now(clock);
    credential.applyMeta(form.name(), form.type(), form.username(), now);
    if (form.secret() != null && !form.secret().isBlank()) {
      credential.replaceSecret(secretVault.encrypt(form.secret(), tenantId), now);
    }
    return credentialRepository.save(credential);
  }

  @Transactional
  public void delete(UUID tenantId, UUID id) {
    credentialRepository.delete(findById(tenantId, id));
  }

  /** 설정법 위키 공간 연동(null이면 해제). 위키 소유 검증은 컨트롤러에서. */
  @Transactional
  public Credential linkWiki(UUID tenantId, UUID id, UUID wikiSpaceId) {
    Credential credential = findById(tenantId, id);
    credential.linkWiki(wikiSpaceId, OffsetDateTime.now());
    return credentialRepository.save(credential);
  }

  /**
   * 제어 엔진 전용: 자격증명을 복호화해 계정+평문 비밀을 돌려준다.
   * 반환값은 즉시 사용 후 폐기하고 로그/감사에 평문을 남기지 않는다.
   */
  public ResolvedCredential resolveSecret(UUID tenantId, UUID id) {
    Credential credential = findById(tenantId, id);
    String secret = secretVault.decrypt(
        credential.getSecretCiphertext(), credential.getDekWrapped(), credential.getKeyVersion(), tenantId);
    return new ResolvedCredential(credential.getUsername(), secret);
  }

  /** 복호화된 자격증명(즉시 사용·폐기 대상). */
  public record ResolvedCredential(String username, String secret) {}
}
