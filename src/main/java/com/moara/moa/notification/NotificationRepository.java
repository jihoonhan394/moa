package com.moara.moa.notification;

import java.time.OffsetDateTime;
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

  /** 같은 제목의 알림을 특정 시각 이후 이미 보냈는지(배치 중복 발송 차단용). */
  boolean existsByTenantIdAndUserIdAndTitleAndCreatedAtAfter(
      UUID tenantId, UUID userId, String title, OffsetDateTime after);
}
