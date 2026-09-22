package com.moara.moa.invitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, UUID> {
  Optional<UserInvitation> findByTokenHash(String tokenHash);

  Optional<UserInvitation> findByTenantIdAndId(UUID tenantId, UUID id);

  List<UserInvitation> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  boolean existsByTenantIdAndEmailIgnoreCaseAndStatus(UUID tenantId, String email, InvitationStatus status);
}
