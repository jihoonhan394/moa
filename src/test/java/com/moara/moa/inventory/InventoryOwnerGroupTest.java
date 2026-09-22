package com.moara.moa.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupForm;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.group.AccessGroupStatus;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 인벤토리 소유팀: 소유팀에 배정하면 그 그룹으로 조회되고, 해제하면 빠진다. */
@SpringBootTest
@ActiveProfiles("test")
class InventoryOwnerGroupTest {
  @Autowired private InventoryItemService inventoryService;
  @Autowired private AccessGroupService groupService;
  @Autowired private TenantService tenantService;

  @Test
  void ownerGroupScopesTeamAssets() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("자산사", "IOG" + System.nanoTime()));
    UUID tenantId = tenant.getId();
    AccessGroup dev = groupService.create(tenantId,
        new AccessGroupForm("개발팀-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
    AccessGroup ops = groupService.create(tenantId,
        new AccessGroupForm("운영팀-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));

    InventoryItem item = inventoryService.create(tenantId, new InventoryItemForm(
        "개발팀 노트북", InventoryItemType.PHYSICAL, "노트북", "SN-1", null, null));

    // 소유팀 미지정: 어떤 팀 조회에도 안 잡힘.
    assertThat(inventoryService.findOwnedByGroups(tenantId, Set.of(dev.getId(), ops.getId()))).isEmpty();

    // 개발팀 소유로 배정 → 개발팀 조회에 잡히고 운영팀엔 안 잡힘.
    inventoryService.assignOwnerGroup(tenantId, item.getId(), dev.getId());
    assertThat(inventoryService.findOwnedByGroups(tenantId, Set.of(dev.getId())))
        .extracting(InventoryItem::getId).containsExactly(item.getId());
    assertThat(inventoryService.findOwnedByGroups(tenantId, Set.of(ops.getId()))).isEmpty();

    // 해제 → 다시 안 잡힘.
    inventoryService.assignOwnerGroup(tenantId, item.getId(), null);
    assertThat(inventoryService.findOwnedByGroups(tenantId, Set.of(dev.getId()))).isEmpty();
  }
}
