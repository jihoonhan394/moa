package com.moara.moa.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.access.AccessApprovalService;
import com.moara.moa.access.AccessRequest;
import com.moara.moa.access.AccessRequestRepository;
import com.moara.moa.access.AccessRequestStatus;
import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetForm;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetStatus;
import com.moara.moa.asset.AssetType;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.FeatureModule;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 접근요청/승인 UI 플로우: 요청→승인→JIT 접속 게이트, 테넌트 격리, 본인 승인 차단. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessApprovalFlowTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private AccessRequestRepository repository;
  @Autowired private AccessApprovalService approvalService;
  @Autowired private AssetService assetService;
  @Autowired private ManagedUserService userService;
  @Autowired private com.moara.moa.tenant.TenantService tenantService;

  @Test
  void requesterRequestsManagerApprovesThenJitConnectPageOpens() throws Exception {
    UUID tenant = tenant();
    ManagedUser requester = user(tenant, UserRole.USER);
    ManagedUser manager = user(tenant, UserRole.INFRA_MANAGER);
    Asset server = server(tenant);

    mockMvc.perform(post("/access-requests").with(authentication(auth(requester))).with(csrf())
            .param("assetId", server.getId().toString())
            .param("reason", "야간 배포 장애 대응")
            .param("durationHours", "4"))
        .andExpect(status().is3xxRedirection());

    AccessRequest req = repository
        .findByTenantIdAndRequesterUserIdOrderByCreatedAtDesc(tenant, requester.getId()).get(0);
    assertThat(req.getStatus()).isEqualTo(AccessRequestStatus.PENDING);

    // 승인자 화면에 대기 요청(서버명)이 보인다.
    mockMvc.perform(get("/access-approvals").with(authentication(auth(manager))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(server.getName())));

    // 승인 → 활성 승인 성립.
    mockMvc.perform(post("/access-approvals/" + req.getId() + "/approve")
            .with(authentication(auth(manager))).with(csrf()))
        .andExpect(status().is3xxRedirection());
    assertThat(approvalService.hasActiveApproval(tenant, requester.getId(), server.getId())).isTrue();

    // 표준 권한이 없어도 JIT 승인으로 접속 화면이 열린다.
    mockMvc.perform(get("/portal/assets/" + server.getId() + "/connect")
            .with(authentication(auth(requester))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(server.getName())));
  }

  @Test
  void requesterCannotApproveOwnRequestViaController() throws Exception {
    UUID tenant = tenant();
    ManagedUser requester = user(tenant, UserRole.USER);
    Asset server = server(tenant);
    AccessRequest req = approvalService.request(tenant, requester.getId(),
        new com.moara.moa.access.AccessRequestForm(server.getId(), "본인 승인 시도", 2));

    // 컨트롤러는 예외를 삼켜 리다이렉트하지만, 상태는 PENDING 유지(승인 안 됨).
    mockMvc.perform(post("/access-approvals/" + req.getId() + "/approve")
            .with(authentication(auth(requester))).with(csrf()))
        .andExpect(status().is3xxRedirection());
    assertThat(repository.findByIdAndTenantId(req.getId(), tenant).orElseThrow().getStatus())
        .isEqualTo(AccessRequestStatus.PENDING);
    assertThat(approvalService.hasActiveApproval(tenant, requester.getId(), server.getId())).isFalse();
  }

  @Test
  void approvalsAreIsolatedByTenant() throws Exception {
    UUID tenantA = tenant();
    ManagedUser requesterA = user(tenantA, UserRole.USER);
    Asset serverA = server(tenantA);
    approvalService.request(tenantA, requesterA.getId(),
        new com.moara.moa.access.AccessRequestForm(serverA.getId(), "격리 확인 요청", 2));

    UUID tenantB = tenant();
    ManagedUser managerB = user(tenantB, UserRole.INFRA_MANAGER);
    // 다른 기관 관리자 승인 화면엔 A의 서버가 보이지 않는다.
    mockMvc.perform(get("/access-approvals").with(authentication(auth(managerB))))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString(serverA.getName()))));
  }

  // ── 픽스처 ──────────────────────────────────────────────────────

  private UUID tenant() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("기관", "AF" + System.nanoTime()));
    // /portal(접속 화면)은 SERVER_ACCESS 기능이 필요(테넌트 기능 게이트).
    tenantService.updateDetail(tenant.getId(), java.time.LocalDate.now().minusDays(1),
        java.time.LocalDate.now().plusYears(1), java.util.Set.of(FeatureModule.SERVER_ACCESS));
    return tenant.getId();
  }

  private ManagedUser user(UUID tenant, UserRole role) {
    String username = "u" + System.nanoTime();
    return userService.create(tenant,
        new UserForm(username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE), role);
  }

  private Asset server(UUID tenant) {
    return assetService.create(tenant, new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "192.0.2.91", 22, "",
        "LINUX", "테스트", AssetStatus.ACTIVE));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
  }
}
