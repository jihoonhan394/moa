package com.moara.moa.solution;

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
import com.moara.moa.remote.RemoteProtocol;
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
 * 솔루션 소유팀: 소유팀에 배정하면 그 팀원은 개인 배정 없이도 운영(제어)할 수 있고,
 * 팀 밖 사용자는 못 본다. 위키의 '공간→그룹 소유' 패턴을 솔루션으로 일반화한 것.
 */
@SpringBootTest
@ActiveProfiles("test")
class SolutionOwnerGroupTest {
  @Autowired private ManagedSolutionService solutionService;
  @Autowired private SolutionAccessService accessService;
  @Autowired private AssetService assetService;
  @Autowired private AccessGroupService groupService;
  @Autowired private ManagedUserService userService;
  @Autowired private TenantService tenantService;

  @Test
  void ownerGroupMemberCanControlWithoutIndividualAssignment() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("소유팀사", "OG" + System.nanoTime()));
    UUID tenantId = tenant.getId();
    Asset server = assetService.create(tenantId, new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "192.0.2.5", 22, "",
        "LINUX", "t", AssetStatus.ACTIVE));
    AccessGroup dev = groupService.create(tenantId,
        new AccessGroupForm("개발팀-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));

    ManagedUser member = user(tenantId);       // 개발팀 소속
    ManagedUser outsider = user(tenantId);      // 팀 밖
    groupService.addMember(tenantId, dev.getId(), member.getId());

    // 인프라팀이 솔루션을 등록하며 소유팀=개발팀 지정(개인 배정은 없음).
    ManagedSolution sol = solutionService.create(tenantId, new SolutionForm(
        server.getId(), "결제서비스", SolutionType.LINUX_DAEMON, "payment.service", null,
        HealthCheckType.NONE, null, SolutionStatus.ACTIVE, null, null, null, RemoteProtocol.SSH, null,
        dev.getId()));

    // 팀원: 개인 배정 없이도 운영 가능 + 내 솔루션 목록에 보임.
    assertThat(accessService.canControl(tenantId, sol.getId(), member.getId())).isTrue();
    assertThat(accessService.assignedSolutions(tenantId, member.getId()))
        .extracting(ManagedSolution::getId).contains(sol.getId());

    // 팀 밖 사용자: 못 봄, 못 제어.
    assertThat(accessService.canControl(tenantId, sol.getId(), outsider.getId())).isFalse();
    assertThat(accessService.assignedSolutions(tenantId, outsider.getId())).isEmpty();
  }

  @Test
  void reassigningOwnerGroupMovesControl() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("소유팀사", "OG" + System.nanoTime()));
    UUID tenantId = tenant.getId();
    Asset server = assetService.create(tenantId, new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "192.0.2.6", 22, "",
        "LINUX", "t", AssetStatus.ACTIVE));
    AccessGroup dev = groupService.create(tenantId,
        new AccessGroupForm("개발팀-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
    ManagedUser member = user(tenantId);
    groupService.addMember(tenantId, dev.getId(), member.getId());

    ManagedSolution sol = solutionService.create(tenantId, new SolutionForm(
        server.getId(), "배치잡", SolutionType.LINUX_DAEMON, "batch.job", null,
        HealthCheckType.NONE, null, SolutionStatus.ACTIVE, null, null, null, RemoteProtocol.SSH, null, null));
    assertThat(accessService.canControl(tenantId, sol.getId(), member.getId())).isFalse();

    // 소유팀 배정 → 제어 가능해짐. 해제 → 다시 불가.
    solutionService.assignOwnerGroup(tenantId, sol.getId(), dev.getId());
    assertThat(accessService.canControl(tenantId, sol.getId(), member.getId())).isTrue();
    solutionService.assignOwnerGroup(tenantId, sol.getId(), null);
    assertThat(accessService.canControl(tenantId, sol.getId(), member.getId())).isFalse();
  }

  private ManagedUser user(UUID tenantId) {
    String username = "u" + System.nanoTime();
    return userService.create(tenantId,
        new UserForm(username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
