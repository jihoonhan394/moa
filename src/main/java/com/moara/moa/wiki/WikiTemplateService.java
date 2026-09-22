package com.moara.moa.wiki;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 위키 서식 관리(공간 스코프). 생성·수정·삭제 권한은 컨트롤러가 공간 MANAGE로 게이팅한다. */
@Service
@Transactional(readOnly = true)
public class WikiTemplateService {
  private final WikiTemplateRepository repository;

  public WikiTemplateService(WikiTemplateRepository repository) {
    this.repository = repository;
  }

  public List<WikiTemplate> findBySpace(UUID tenantId, UUID spaceId) {
    return repository.findAllByTenantIdAndSpaceIdOrderByNameAsc(tenantId, spaceId);
  }

  public WikiTemplate findById(UUID tenantId, UUID id) {
    return repository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new WikiTemplateNotFoundException(id));
  }

  @Transactional
  public WikiTemplate create(UUID tenantId, UUID spaceId, UUID userId, WikiTemplateForm form) {
    return repository.save(new WikiTemplate(UUID.randomUUID(), tenantId, spaceId, userId, form, OffsetDateTime.now()));
  }

  @Transactional
  public void delete(UUID tenantId, UUID id) {
    repository.delete(findById(tenantId, id));
  }
}
