package com.moara.moa.wiki;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 위키 문서 관리. 기관 사용자 협업 편집(작성·수정은 전원, 삭제 권한은 컨트롤러에서 판정). */
@Service
@Transactional(readOnly = true)
public class WikiPageService {
  private final WikiPageRepository repository;
  private final WikiPageRevisionRepository revisionRepository;

  public WikiPageService(WikiPageRepository repository, WikiPageRevisionRepository revisionRepository) {
    this.repository = repository;
    this.revisionRepository = revisionRepository;
  }

  public List<WikiPage> findAll(UUID tenantId) {
    return repository.findAllByTenantIdOrderByUpdatedAtDesc(tenantId);
  }

  public List<WikiPage> findBySpace(UUID tenantId, UUID spaceId) {
    return repository.findAllByTenantIdAndSpaceIdOrderByUpdatedAtDesc(tenantId, spaceId);
  }

  /** 제목·본문 검색(권한 필터는 호출측에서). 빈 검색어는 빈 결과. */
  public List<WikiPage> search(UUID tenantId, String query) {
    if (query == null || query.isBlank()) {
      return java.util.List.of();
    }
    return repository.search(tenantId, query.trim());
  }

  public WikiPage findById(UUID tenantId, UUID id) {
    return repository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new WikiPageNotFoundException(id));
  }

  @Transactional
  public WikiPage create(UUID tenantId, UUID spaceId, UUID authorUserId, WikiPageForm form) {
    return repository.save(new WikiPage(
        UUID.randomUUID(), tenantId, spaceId, authorUserId, form, OffsetDateTime.now()));
  }

  @Transactional
  public WikiPage update(UUID tenantId, UUID id, UUID editorUserId, WikiPageForm form) {
    WikiPage page = findById(tenantId, id);
    snapshot(page); // 수정 직전 상태를 이력으로 남김
    page.edit(editorUserId, form, OffsetDateTime.now());
    return repository.save(page);
  }

  @Transactional
  public void delete(UUID tenantId, UUID id) {
    repository.delete(findById(tenantId, id));
  }

  // ── 버전 이력 ────────────────────────────────────────────────────────────
  public List<WikiPageRevision> revisions(UUID tenantId, UUID pageId) {
    return revisionRepository.findAllByTenantIdAndPageIdOrderByCreatedAtDesc(tenantId, pageId);
  }

  public WikiPageRevision revision(UUID tenantId, UUID revisionId) {
    return revisionRepository.findByTenantIdAndId(tenantId, revisionId)
        .orElseThrow(() -> new WikiPageNotFoundException(revisionId));
  }

  /** 지정 버전으로 되돌린다. 현재 상태도 이력으로 남긴다. */
  @Transactional
  public WikiPage restore(UUID tenantId, UUID pageId, UUID revisionId, UUID editorUserId) {
    WikiPage page = findById(tenantId, pageId);
    WikiPageRevision revision = revision(tenantId, revisionId);
    if (!page.getId().equals(revision.getPageId())) {
      throw new WikiPageNotFoundException(revisionId);
    }
    snapshot(page);
    page.edit(editorUserId, new WikiPageForm(revision.getTitle(), revision.getContent()), OffsetDateTime.now());
    return repository.save(page);
  }

  private void snapshot(WikiPage page) {
    revisionRepository.save(new WikiPageRevision(
        UUID.randomUUID(), page.getTenantId(), page.getId(), page.getTitle(), page.getContent(),
        page.getUpdatedByUserId(), OffsetDateTime.now()));
  }
}
