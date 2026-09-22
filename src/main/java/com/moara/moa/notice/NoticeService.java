package com.moara.moa.notice;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기관 공지사항 CRUD. 모든 조회/수정은 tenantId로 격리한다(교차기관 접근 차단). */
@Service
@Transactional(readOnly = true)
public class NoticeService {
  private final NoticeRepository repository;

  public NoticeService(NoticeRepository repository) {
    this.repository = repository;
  }

  public List<Notice> list(UUID tenantId) {
    return repository.findByTenantIdOrderByPinnedDescCreatedAtDesc(tenantId);
  }

  public Notice get(UUID tenantId, UUID id) {
    return repository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new NoticeNotFoundException(id));
  }

  @Transactional
  public Notice create(UUID tenantId, NoticeForm form, UUID authorId, String authorName) {
    Notice notice = Notice.create(
        tenantId, form.title(), form.body(), form.pinned(), authorId, authorName, OffsetDateTime.now());
    return repository.save(notice);
  }

  @Transactional
  public Notice update(UUID tenantId, UUID id, NoticeForm form) {
    Notice notice = get(tenantId, id);
    notice.apply(form.title(), form.body(), form.pinned(), OffsetDateTime.now());
    return notice;
  }

  @Transactional
  public void delete(UUID tenantId, UUID id) {
    Notice notice = get(tenantId, id);
    repository.delete(notice);
  }
}
