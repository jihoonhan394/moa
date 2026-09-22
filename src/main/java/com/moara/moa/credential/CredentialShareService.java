package com.moara.moa.credential;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크리덴셜 열람 공유. INFRA 관리자가 특정 자격증명을 특정 사용자에게 공유하면, 그 사용자는 '내 크리덴셜'에서
 * 열람할 수 있다(비IT 사용자의 공유기/프린터 id·pw 확인). 열람 자체의 감사는 호출 컨트롤러가 남긴다.
 */
@Service
@Transactional(readOnly = true)
public class CredentialShareService {
  private final CredentialShareRepository shareRepository;
  private final CredentialService credentialService;

  public CredentialShareService(
      CredentialShareRepository shareRepository, CredentialService credentialService) {
    this.shareRepository = shareRepository;
    this.credentialService = credentialService;
  }

  /** 크리덴셜을 사용자에게 공유(멱등). */
  @Transactional
  public void share(UUID tenantId, UUID credentialId, UUID userId) {
    credentialService.findById(tenantId, credentialId); // 소유 검증
    if (!shareRepository.existsByTenantIdAndCredentialIdAndUserId(tenantId, credentialId, userId)) {
      shareRepository.save(new CredentialShare(
          UUID.randomUUID(), tenantId, credentialId, userId, OffsetDateTime.now()));
    }
  }

  @Transactional
  public void unshare(UUID tenantId, UUID credentialId, UUID userId) {
    shareRepository.deleteByTenantIdAndCredentialIdAndUserId(tenantId, credentialId, userId);
  }

  /** 특정 크리덴셜을 공유받은 사용자 식별자(관리 화면 표시용). */
  public Set<UUID> sharedUserIds(UUID tenantId, UUID credentialId) {
    return shareRepository.findAllByTenantIdAndCredentialId(tenantId, credentialId).stream()
        .map(CredentialShare::getUserId)
        .collect(Collectors.toSet());
  }

  /** 이 사용자가 공유받은 크리덴셜 목록('내 크리덴셜'). */
  public List<Credential> sharedCredentials(UUID tenantId, UUID userId) {
    Set<UUID> ids = shareRepository.findAllByTenantIdAndUserId(tenantId, userId).stream()
        .map(CredentialShare::getCredentialId)
        .collect(Collectors.toSet());
    return credentialService.findAll(tenantId).stream()
        .filter(c -> ids.contains(c.getId()))
        .toList();
  }

  public boolean canReveal(UUID tenantId, UUID credentialId, UUID userId) {
    return userId != null
        && shareRepository.existsByTenantIdAndCredentialIdAndUserId(tenantId, credentialId, userId);
  }

  /**
   * 공유받은 크리덴셜을 복호화해 돌려준다. 공유가 없으면 예외(권한 없음). 감사는 호출부.
   */
  public CredentialService.ResolvedCredential reveal(UUID tenantId, UUID credentialId, UUID userId) {
    if (!canReveal(tenantId, credentialId, userId)) {
      throw new CredentialNotFoundException(credentialId); // 존재/권한 노출 안 함
    }
    return credentialService.resolveSecret(tenantId, credentialId);
  }

  /** 퇴사 회수: 이 사용자의 모든 크리덴셜 공유 삭제. */
  @Transactional
  public long removeSharesOf(UUID tenantId, UUID userId) {
    return shareRepository.deleteByTenantIdAndUserId(tenantId, userId);
  }
}
