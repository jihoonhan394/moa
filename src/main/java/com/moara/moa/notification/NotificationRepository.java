package com.moara.moa.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  List<Notification> findTop20ByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId, UUID userId);

  List<Notification> findTop5ByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId, UUID userId);

  long countByTenantIdAndUserIdAndReadFalse(UUID tenantId, UUID userId);

  List<Notification> findAllByTenantIdAndUserIdAndReadFalse(UUID tenantId, UUID userId);

  Optional<Notification> findByTenantIdAndId(UUID tenantId, UUID id);
}
