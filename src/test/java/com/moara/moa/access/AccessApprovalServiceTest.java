package com.moara.moa.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 접근요청/승인(JIT) 서비스 검증: 승인·게이트·maker-checker·회수·만료·테넌트 격리. */
@SpringBootTest
@ActiveProfiles("test")
class AccessApprovalServiceTest {
  @Autowired private AccessApprovalService service;
  @Autowired private AccessRequestRepository repository;
  @Autowired private AssetService assetService;
  @Autowired private ManagedUserService userService;
  @Autowired private AccessGroupService groupService;

  @Test
  void requestCreatesPendingAndManagerApprovalGrantsActiveAccess() {
    Fixture f = fixture();
    AccessRequest req = service.request(f.tenant, f.requester,
        new AccessRequestForm(f.asset, "긴급 점검이 필요합니다.", 4));
    assertThat(req.getStatus()).isEqualTo(AccessRequestStatus.PENDING);
    assertThat(service.hasActiveApproval(f.tenant, f.requester, f.asset)).isFalse();

    service.approve(f.tenant, req.getId(), f.manager, null, "확인");
    assertThat(repository.findByIdAndTenantId(req.getId(), f.tenant).orElseThrow().getStatus())
        .isEqualTo(AccessRequestStatus.APPROVED);
    assertThat(service.hasActiveApproval(f.tenant, f.requester, f.asset)).isTrue();
  }

  @Test
  void requesterCannotApproveOwnRequest() {
    Fixture f = fixture();
    AccessRequest req = service.request(f.tenant, f.requester,
        new AccessRequestForm(f.asset, "본인 승인 시도", 2));
    assertThatThrownBy(() -> service.approve(f.tenant, req.getId(), f.requester, null, null))
        .isInstanceOf(AccessApprovalDeniedException.class);
  }

  @Test
  void plainUserCannotApprove() {
    Fixture f = fixture();
    ManagedUser bystander = user(f.tenant, UserRole.USER);
    AccessRequest req = service.request(f.tenant, f.requester,
        new AccessRequestForm(f.asset, "일반 사용자 승인 시도", 2));
    assertThatThrownBy(() -> service.approve(f.tenant, req.getId(), bystander.getId(), null, null))
        .isInstanceOf(AccessApprovalDeniedException.class);
  }

  @Test
  void ownerGroupLeaderCanApprove() {
    Fixture f = fixture();
    ManagedUser leader = user(f.tenant, UserRole.USER);
    AccessGroup group = groupService.create(f.tenant,
        new AccessGroupForm("team-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
    groupService.addMember(f.tenant, group.getId(), leader.getId());
    groupService.setLeader(f.tenant, group.getId(), leader.getId(), true);
    assetService.assignOwnerGroup(f.tenant, f.asset, group.getId());

    AccessRequest req = service.request(f.tenant, f.requester,
        new AccessRequestForm(f.asset, "소유팀 리더 승인", 2));
    service.approve(f.tenant, req.getId(), leader.getId(), null, null);
    assertThat(service.hasActiveApproval(f.tenant, f.requester, f.asset)).isTrue();
  }

  @Test
  void cancelByRequesterStopsApproval() {
    Fixture f = fixture();
    AccessRequest req = service.request(f.tenant, f.requester,
        new AccessRequestForm(f.asset, "취소할 요청", 2));
    service.cancel(f.tenant, req.getId(), f.requester);
    assertThat(repository.findByIdAndTenantId(req.getId(), f.tenant).orElseThrow().getStatus())
        .isEqualTo(AccessRequestStatus.CANCELED);
  }

  @Test
  void revokeDropsActiveAccess() {
    Fixture f = fixture();
    AccessRequest req = service.request(f.tenant, f.requester,
        new AccessRequestForm(f.asset, "회수 대상", 4));
    service.approve(f.tenant, req.getId(), f.manager, null, null);
    assertThat(service.hasActiveApproval(f.tenant, f.requester, f.asset)).isTrue();

    service.revoke(f.tenant, req.getId(), f.manager, "정책 위반");
    assertThat(service.hasActiveApproval(f.tenant, f.requester, f.asset)).isFalse();
    assertThat(repository.findByIdAndTenantId(req.getId(), f.tenant).orElseThrow().getStatus())
        .isEqualTo(AccessRequestStatus.REVOKED);
  }

  @Test
  void requestIsHiddenFromOtherTenant() {
    Fixture f = fixture();
    AccessRequest req = service.request(f.tenant, f.requester,
        new AccessRequestForm(f.asset, "격리 확인", 2));
    UUID otherTenant = tenant();
    ManagedUser otherManager = user(otherTenant, UserRole.INFRA_MANAGER);
    assertThatThrownBy(() -> service.approve(otherTenant, req.getId(), otherManager.getId(), null, null))
        .isInstanceOf(AccessRequestNotFoundException.class);
  }

  @Test
  void pastDueApprovalIsInactiveAndExpireDueFlipsStatus() {
    Fixture f = fixture();
    // 창이 이미 지난 승인 건을 직접 저장(2일 전 [start, start+1h]).
    OffsetDateTime past = OffsetDateTime.now().minusDays(2);
    AccessRequest req = AccessRequest.create(
        f.tenant, f.requester, f.asset, "과거 승인", past, past.plusHours(1), past);
    req.approve(f.manager, null, null, past);
    repository.save(req);

    assertThat(service.hasActiveApproval(f.tenant, f.requester, f.asset)).isFalse(); // 창 밖
    assertThat(service.expireDue()).isGreaterThanOrEqualTo(1);
    assertThat(repository.findByIdAndTenantId(req.getId(), f.tenant).orElseThrow().getStatus())
        .isEqualTo(AccessRequestStatus.EXPIRED);
  }

  // ── 픽스처 ──────────────────────────────────────────────────────

  private record Fixture(UUID tenant, UUID requester, UUID manager, UUID asset) {}

  private Fixture fixture() {
    UUID tenant = tenant();
    UUID requester = user(tenant, UserRole.USER).getId();
    UUID manager = user(tenant, UserRole.INFRA_MANAGER).getId();
    Asset asset = assetService.create(tenant, new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "192.0.2.90", 22, "",
        "LINUX", "테스트 서버", AssetStatus.ACTIVE));
    return new Fixture(tenant, requester, manager, asset.getId());
  }

  private UUID tenant() {
    return tenantService.createTenant(new CreateTenantCommand("기관", "AR" + System.nanoTime())).getId();
  }

  @Autowired private com.moara.moa.tenant.TenantService tenantService;

  private ManagedUser user(UUID tenant, UserRole role) {
    String username = "u" + System.nanoTime();
    return userService.create(tenant,
        new UserForm(username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE), role);
  }
}
