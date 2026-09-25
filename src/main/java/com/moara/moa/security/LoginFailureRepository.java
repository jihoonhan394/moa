package com.moara.moa.security;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginFailureRepository extends JpaRepository<LoginFailure, UUID> {
  long countByTenantIdAndUsernameIgnoreCaseAndAttemptedAtAfter(
      UUID tenantId, String username, OffsetDateTime after);

  long countByClientIpAndAttemptedAtAfter(String clientIp, OffsetDateTime after);

  void deleteByTenantIdAndUsernameIgnoreCase(UUID tenantId, String username);

  void deleteByAttemptedAtBefore(OffsetDateTime before);
}
