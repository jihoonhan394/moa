package com.moara.moa.wiki;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 위키 편의기능: 라벨·댓글·즐겨찾기. 접근 권한(공간)은 컨트롤러가 게이팅한다. */
@Service
@Transactional(readOnly = true)
public class WikiEngagementService {
  private final WikiPageLabelRepository labelRepository;
  private final WikiCommentRepository commentRepository;
  private final WikiFavoriteRepository favoriteRepository;

  public WikiEngagementService(
      WikiPageLabelRepository labelRepository, WikiCommentRepository commentRepository,
      WikiFavoriteRepository favoriteRepository) {
    this.labelRepository = labelRepository;
    this.commentRepository = commentRepository;
    this.favoriteRepository = favoriteRepository;
  }

  // ── 라벨 ─────────────────────────────────────────────────────────────────
  public List<WikiPageLabel> labels(UUID tenantId, UUID pageId) {
    return labelRepository.findAllByTenantIdAndPageIdOrderByLabelAsc(tenantId, pageId);
  }

  @Transactional
  public void addLabel(UUID tenantId, UUID pageId, String raw) {
    if (raw == null || raw.isBlank()) {
      return;
    }
    String label = raw.trim();
    if (label.length() > 50) {
      label = label.substring(0, 50);
    }
    if (!labelRepository.existsByTenantIdAndPageIdAndLabel(tenantId, pageId, label)) {
      labelRepository.save(new WikiPageLabel(UUID.randomUUID(), tenantId, pageId, label, OffsetDateTime.now()));
    }
  }

  @Transactional
  public void removeLabel(UUID tenantId, UUID labelId) {
    labelRepository.findByTenantIdAndId(tenantId, labelId).ifPresent(labelRepository::delete);
  }

  // ── 댓글 ─────────────────────────────────────────────────────────────────
  public List<WikiComment> comments(UUID tenantId, UUID pageId) {
    return commentRepository.findAllByTenantIdAndPageIdOrderByCreatedAtAsc(tenantId, pageId);
  }

  public WikiComment comment(UUID tenantId, UUID commentId) {
    return commentRepository.findByTenantIdAndId(tenantId, commentId)
        .orElseThrow(() -> new WikiPageNotFoundException(commentId));
  }

  @Transactional
  public WikiComment addComment(UUID tenantId, UUID pageId, UUID authorUserId, String content) {
    return commentRepository.save(new WikiComment(
        UUID.randomUUID(), tenantId, pageId, authorUserId, content.trim(), OffsetDateTime.now()));
  }

  @Transactional
  public void deleteComment(UUID tenantId, UUID commentId) {
    commentRepository.delete(comment(tenantId, commentId));
  }

  // ── 즐겨찾기 ─────────────────────────────────────────────────────────────
  public boolean isFavorite(UUID tenantId, UUID userId, UUID pageId) {
    return userId != null && favoriteRepository.existsByTenantIdAndUserIdAndPageId(tenantId, userId, pageId);
  }

  /** 즐겨찾기 토글. 반환값=토글 후 즐겨찾기 상태. */
  @Transactional
  public boolean toggleFavorite(UUID tenantId, UUID userId, UUID pageId) {
    return favoriteRepository.findByTenantIdAndUserIdAndPageId(tenantId, userId, pageId)
        .map(existing -> {
          favoriteRepository.delete(existing);
          return false;
        })
        .orElseGet(() -> {
          favoriteRepository.save(new WikiFavorite(UUID.randomUUID(), tenantId, userId, pageId, OffsetDateTime.now()));
          return true;
        });
  }

  public Set<UUID> favoritePageIds(UUID tenantId, UUID userId) {
    return favoriteRepository.findAllByTenantIdAndUserId(tenantId, userId).stream()
        .map(WikiFavorite::getPageId)
        .collect(Collectors.toSet());
  }
}
