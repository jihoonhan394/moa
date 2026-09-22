package com.moara.moa.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetForm;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetStatus;
import com.moara.moa.asset.AssetType;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupForm;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.group.AccessGroupStatus;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 자산 소유팀: 서버를 팀 소유로 배정하면 그 팀원은 별도 권한 없이도 접근 가능하고, 팀 밖은 불가.
 * 소유팀 접근은 기존 권한 경로에 <b>가산</b>된다(민감 권한 SQL 불변).
 */
@SpringBootTest
@ActiveProfiles("test")
class AssetOwnerGroupAccessTest {
  @Autowired private AssetService assetService;
  @Autowired private AccessibleAssetService accessibleAssetService;
  @Autowired private AccessGroupService groupService;
  @Autowired private ManagedUserService userService;
  @Autowired private TenantService tenantService;

  @Test
  void ownerTeamMemberCanAccessWithoutPermissionGrant() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("인프라사", "AOG" + System.nanoTime()));
    UUID tenantId = tenant.getId();
    Asset server = assetService.create(tenantId, new AssetForm(
        "운영-웹-01", AssetType.SERVER, AssetProtocol.SSH, "10.0.0.9", 22, "", "LINUX", "t", AssetStatus.ACTIVE));
    AccessGroup infra = groupService.create(tenantId,
        new AccessGroupForm("인프라팀-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
    ManagedUser member = user(tenantId);
    ManagedUser outsider = user(tenantId);
    groupService.addMember(tenantId, infra.getId(), member.getId());

    // 소유팀 배정 전: 권한 없으면 접근 불가.
    assertThat(accessibleAssetService.canAccess(tenantId, member.getId(), server.getId())).isFalse();

    assetService.assignOwnerGroup(tenantId, server.getId(), infra.getId());

    // 팀원: 소유팀 경로로 접근 가능 + 접근목록에 뜸. 팀 밖: 여전히 불가.
    assertThat(accessibleAssetService.canAccess(tenantId, member.getId(), server.getId())).isTrue();
    assertThat(accessibleAssetService.findAccessibleAssets(tenantId, member.getId()))
        .extracting(Asset::getId).contains(server.getId());
    assertThat(accessibleAssetService.canAccess(tenantId, outsider.getId(), server.getId())).isFalse();

    // 해제 → 다시 불가.
    assetService.assignOwnerGroup(tenantId, server.getId(), null);
    assertThat(accessibleAssetService.canAccess(tenantId, member.getId(), server.getId())).isFalse();
  }

  private ManagedUser user(UUID tenantId) {
    String username = "u" + System.nanoTime();
    return userService.create(tenantId,
        new UserForm(username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
