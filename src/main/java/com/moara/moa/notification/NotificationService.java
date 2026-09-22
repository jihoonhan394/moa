package com.moara.moa.notification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인앱 알림 발행/조회/읽음 처리. 다른 모듈이 notify(...)로 알림을 남긴다. */
@Service
@Transactional(readOnly = true)
public class NotificationService {
  private final NotificationRepository repository;

  public NotificationService(NotificationRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void notify(UUID tenantId, UUID userId, String title, String body, String link) {
    if (userId == null) {
      return;
    }
    repository.save(new Notification(UUID.randomUUID(), tenantId, userId, title, body, link, OffsetDateTime.now()));
  }

  public long unreadCount(UUID tenantId, UUID userId) {
    return userId == null ? 0 : repository.countByTenantIdAndUserIdAndReadFalse(tenantId, userId);
  }

  public List<Notification> recent(UUID tenantId, UUID userId) {
    return userId == null ? List.of() : repository.findTop5ByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId);
  }

  public List<Notification> list(UUID tenantId, UUID userId) {
    return userId == null ? List.of() : repository.findTop20ByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId);
  }

  @Transactional
  public void markAllRead(UUID tenantId, UUID userId) {
    if (userId == null) {
      return;
    }
    List<Notification> unread = repository.findAllByTenantIdAndUserIdAndReadFalse(tenantId, userId);
    unread.forEach(Notification::markRead);
    repository.saveAll(unread);
  }
}
