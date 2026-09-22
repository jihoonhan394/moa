package com.moara.moa.reservation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공유자산 관리(자산 관리자). 기관 스코프 CRUD + 중복 이름 방지. */
@Service
@Transactional(readOnly = true)
public class SharedResourceService {
  private final SharedResourceRepository repository;

  public SharedResourceService(SharedResourceRepository repository) {
    this.repository = repository;
  }

  public List<SharedResource> findAll(UUID tenantId) {
    return repository.findAllByTenantIdOrderByNameAsc(tenantId);
  }

  public SharedResource findById(UUID tenantId, UUID id) {
    return repository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new SharedResourceNotFoundException(id));
  }

  @Transactional
  public SharedResource create(UUID tenantId, SharedResourceForm form) {
    requireUniqueName(tenantId, form.name(), null);
    return repository.save(new SharedResource(UUID.randomUUID(), tenantId, form, OffsetDateTime.now()));
  }

  @Transactional
  public SharedResource update(UUID tenantId, UUID id, SharedResourceForm form) {
    SharedResource resource = findById(tenantId, id);
    requireUniqueName(tenantId, form.name(), id);
    resource.apply(form, OffsetDateTime.now());
    return repository.save(resource);
  }

  @Transactional
  public void delete(UUID tenantId, UUID id) {
    repository.delete(findById(tenantId, id));
  }

  private void requireUniqueName(UUID tenantId, String name, UUID excludeId) {
    repository.findByTenantIdAndName(tenantId, name).ifPresent(existing -> {
      if (!existing.getId().equals(excludeId)) {
        throw new DuplicateSharedResourceException(name);
      }
    });
  }
}
