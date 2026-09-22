package com.moara.moa.deputy;

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
import com.moara.moa.permission.AccessibleAssetService;
import com.moara.moa.remote.RemoteProtocol;
import com.moara.moa.solution.HealthCheckType;
import com.moara.moa.solution.ManagedSolution;
import com.moara.moa.solution.ManagedSolutionService;
import com.moara.moa.solution.SolutionAccessService;
import com.moara.moa.solution.SolutionForm;
import com.moara.moa.solution.SolutionStatus;
import com.moara.moa.solution.SolutionType;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 팀 내 대직: 활성 기간엔 대직자가 부재자의 서버 접근·솔루션 제어를 상속하고, 기간 밖/종료 후엔 아니다. */
@SpringBootTest
@ActiveProfiles("test")
class DeputyAccessTest {
  @Autowired private DeputyService deputyService;
  @Autowired private SolutionAccessService solutionAccessService;
  @Autowired private AccessibleAssetService accessibleAssetService;
  @Autowired private ManagedSolutionService solutionService;
  @Autowired private AssetService assetService;
  @Autowired private AccessGroupService groupService;
  @Autowired private ManagedUserService userService;
  @Autowired private TenantService tenantService;

  @Test
  void deputyInheritsAbsentAccessWithinWindowOnly() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("대직사", "DEP" + System.nanoTime()));
    UUID tenantId = tenant.getId();
    ManagedUser absent = user(tenantId);
    ManagedUser deputy = user(tenantId);

    // 부재자만 속한 그룹 H가 소유한 서버 K → 부재자만 접근.
    AccessGroup onlyAbsent = groupService.create(tenantId,
        new AccessGroupForm("부재전용-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
    groupService.addMember(tenantId, onlyAbsent.getId(), absent.getId());
    Asset server = assetService.create(tenantId, new AssetForm(
        "부재서버-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "10.0.9.9", 22, "",
        "LINUX", "", AssetStatus.ACTIVE));
    assetService.assignOwnerGroup(tenantId, server.getId(), onlyAbsent.getId());

    // 부재자에게 개인 배정된 솔루션 S(소유팀 없음).
    ManagedSolution sol = solutionService.create(tenantId, new SolutionForm(
        server.getId(), "부재솔루션", SolutionType.LINUX_DAEMON, "svc.x", null,
        HealthCheckType.NONE, null, SolutionStatus.ACTIVE, null, null, null, RemoteProtocol.SSH, null, null));
    solutionAccessService.assignUser(tenantId, sol.getId(), absent.getId());

    // 대직 전: 대직자는 접근·제어 불가.
    assertThat(accessibleAssetService.canAccess(tenantId, deputy.getId(), server.getId())).isFalse();
    assertThat(solutionAccessService.canControl(tenantId, sol.getId(), deputy.getId())).isFalse();

    // 활성 대직(어제~내일) → 상속.
    deputyService.create(tenantId, absent.getId(), deputy.getId(),
        LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), null);
    assertThat(accessibleAssetService.canAccess(tenantId, deputy.getId(), server.getId())).isTrue();
    assertThat(solutionAccessService.canControl(tenantId, sol.getId(), deputy.getId())).isTrue();
    assertThat(accessibleAssetService.findAccessibleAssets(tenantId, deputy.getId()))
        .extracting(Asset::getId).contains(server.getId());
    assertThat(solutionAccessService.assignedSolutions(tenantId, deputy.getId()))
        .extracting(ManagedSolution::getId).contains(sol.getId());
  }

  @Test
  void expiredDeputyDoesNotInherit() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("대직사", "DEP" + System.nanoTime()));
    UUID tenantId = tenant.getId();
    ManagedUser absent = user(tenantId);
    ManagedUser deputy = user(tenantId);
    AccessGroup onlyAbsent = groupService.create(tenantId,
        new AccessGroupForm("부재전용-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
    groupService.addMember(tenantId, onlyAbsent.getId(), absent.getId());
    Asset server = assetService.create(tenantId, new AssetForm(
        "부재서버-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "10.0.9.8", 22, "",
        "LINUX", "", AssetStatus.ACTIVE));
    assetService.assignOwnerGroup(tenantId, server.getId(), onlyAbsent.getId());

    // 지난 기간의 대직 → 상속 없음.
    deputyService.create(tenantId, absent.getId(), deputy.getId(),
        LocalDate.now().minusDays(10), LocalDate.now().minusDays(3), null);
    assertThat(accessibleAssetService.canAccess(tenantId, deputy.getId(), server.getId())).isFalse();
  }

  private ManagedUser user(UUID tenantId) {
    String username = "u" + System.nanoTime();
    return userService.create(tenantId,
        new UserForm(username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
