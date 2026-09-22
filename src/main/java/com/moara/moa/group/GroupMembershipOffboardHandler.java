package com.moara.moa.group;

import com.moara.moa.user.OffboardHandler;
import com.moara.moa.user.OffboardOutcome;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 퇴사 회수: 사용자의 모든 그룹(부서) 멤버십 삭제. */
@Component
public class GroupMembershipOffboardHandler implements OffboardHandler {
  private final UserGroupMemberRepository repository;

  public GroupMembershipOffboardHandler(UserGroupMemberRepository repository) {
    this.repository = repository;
  }

  @Override
  public OffboardOutcome offboard(UUID tenantId, UUID userId) {
    return new OffboardOutcome("그룹 멤버십", repository.deleteByTenantIdAndUserId(tenantId, userId));
  }
}
