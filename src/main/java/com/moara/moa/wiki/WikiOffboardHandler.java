package com.moara.moa.wiki;

import com.moara.moa.user.OffboardHandler;
import com.moara.moa.user.OffboardOutcome;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 퇴사 회수: 위키 공간에 개인으로 직접 부여된 권한 삭제(부서 권한은 그룹 탈퇴로 전이 회수). */
@Component
public class WikiOffboardHandler implements OffboardHandler {
  private final WikiSpacePermissionRepository repository;

  public WikiOffboardHandler(WikiSpacePermissionRepository repository) {
    this.repository = repository;
  }

  @Override
  public OffboardOutcome offboard(UUID tenantId, UUID userId) {
    return new OffboardOutcome("위키 개인권한",
        repository.deleteByTenantIdAndSubjectTypeAndSubjectId(tenantId, WikiSubjectType.USER, userId));
  }
}
