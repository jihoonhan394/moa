package com.moara.moa.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 사용자-그룹 N:M 매핑. tenant_id를 함께 저장해 격리·조인 성능을 확보한다.
 * user/group의 테넌트 일치는 Service 계층에서 검증한다(교차 테넌트 매핑 차단).
 */
@Entity
@Table(name = "user_group_members")
public class UserGroupMember {
  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  /** 부서장 여부(부서=그룹의 책임자). 위키 공간 관리 등 위임 거버넌스의 기준점. */
  @Column(nullable = false)
  private boolean leader;

  protected UserGroupMember() {}

  public UserGroupMember(UUID id, UUID tenantId, UUID userId, UUID groupId, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.userId = userId;
    this.groupId = groupId;
    this.createdAt = now;
    this.leader = false;
  }

  /** 부서장 지정/해제. */
  public void setLeader(boolean leader) {
    this.leader = leader;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getUserId() { return userId; }
  public UUID getGroupId() { return groupId; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public boolean isLeader() { return leader; }
}
