package com.moara.moa.credential;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialShareRepository extends JpaRepository<CredentialShare, UUID> {
  List<CredentialShare> findAllByTenantIdAndUserId(UUID tenantId, UUID userId);

  List<CredentialShare> findAllByTenantIdAndCredentialId(UUID tenantId, UUID credentialId);

  boolean existsByTenantIdAndCredentialIdAndUserId(UUID tenantId, UUID credentialId, UUID userId);

  void deleteByTenantIdAndCredentialIdAndUserId(UUID tenantId, UUID credentialId, UUID userId);

  /** 퇴사 회수: 이 사용자의 모든 크리덴셜 공유 삭제. 삭제 건수 반환. */
  long deleteByTenantIdAndUserId(UUID tenantId, UUID userId);
}
