package com.moara.moa.notice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, UUID> {
  /** 기관 공지 목록: 고정핀 우선, 최신순. */
  List<Notice> findByTenantIdOrderByPinnedDescCreatedAtDesc(UUID tenantId);

  /** 교차기관 접근 방지를 위해 id와 tenantId를 함께 조회한다. */
  Optional<Notice> findByIdAndTenantId(UUID id, UUID tenantId);
}
