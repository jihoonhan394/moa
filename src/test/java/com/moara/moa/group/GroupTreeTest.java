package com.moara.moa.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 그룹 조직 트리(상위 그룹, 깊이) 구성과 이동 시 순환 차단을 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
class GroupTreeTest {
  @Autowired private AccessGroupService groupService;
  @Autowired private TenantService tenantService;

  @Test
  void buildsTreeWithDepthAndBlocksCycles() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("조직사", "GT" + System.nanoTime()));
    UUID t = tenant.getId();
    AccessGroup root = groupService.create(t, form("본부", null));
    AccessGroup team = groupService.create(t, form("영업팀", root.getId()));
    AccessGroup cell = groupService.create(t, form("영업1셀", team.getId()));

    var tree = groupService.tree(t);
    assertThat(tree).hasSize(3);
    // 전위순회: 본부(0) → 영업팀(1) → 영업1셀(2).
    assertThat(tree).extracting(n -> n.group().getName()).containsExactly("본부", "영업팀", "영업1셀");
    assertThat(tree).extracting(AccessGroupService.GroupNode::depth).containsExactly(0, 1, 2);

    // 순환 차단: 본부를 자기 후손(영업1셀) 밑으로 이동 불가.
    assertThatThrownBy(() -> groupService.move(t, root.getId(), cell.getId()))
        .isInstanceOf(IllegalArgumentException.class);

    // 정상 이동: 영업1셀을 최상위로.
    groupService.move(t, cell.getId(), null);
    assertThat(groupService.findById(t, cell.getId()).getParentId()).isNull();
  }

  private AccessGroupForm form(String name, UUID parentId) {
    return new AccessGroupForm(name, null, AccessGroupStatus.ACTIVE, parentId);
  }
}
