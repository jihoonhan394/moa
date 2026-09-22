package com.moara.moa.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetForm;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetStatus;
import com.moara.moa.asset.AssetType;
import com.moara.moa.deputy.DeputyService;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupForm;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.group.AccessGroupStatus;
import com.moara.moa.permission.Permission;
import com.moara.moa.permission.PermissionForm;
import com.moara.moa.permission.PermissionProtocol;
import com.moara.moa.permission.PermissionSetService;
import com.moara.moa.permission.PermissionStatus;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.FeatureModule;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 라이브 E2E가 '/portal에 접근 가능 서버가 안 뜬다'고 보고한 3경로(소유팀·권한세트·대직)를 컨트롤러+템플릿
 * 통합으로 재현해 코드 버그 여부를 가린다. 통과하면 E2E 실패는 하니스/누적데이터 문제(코드 정상)로 판정.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalAccessFlowTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;
  @Autowired private AccessGroupService groupService;
  @Autowired private AssetService assetService;
  @Autowired private PermissionSetService permissionService;
  @Autowired private DeputyService deputyService;

  @Test
  void ownerTeamServerAppearsInMemberPortal() throws Exception {
    var t = tenant();
    ManagedUser staff = user(t);
    AccessGroup dev = group(t);
    groupService.addMember(t, dev.getId(), staff.getId());
    Asset srv = server(t, "소유팀서버");
    assetService.assignOwnerGroup(t, srv.getId(), dev.getId());

    mockMvc.perform(get("/portal").with(authentication(auth(staff))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("소유팀서버")));
  }

  @Test
  void permissionGrantedServerAppearsInMemberPortal() throws Exception {
    var t = tenant();
    ManagedUser staff = user(t);
    AccessGroup dev = group(t);
    groupService.addMember(t, dev.getId(), staff.getId());
    Asset srv = server(t, "권한서버");
    Permission p = permissionService.create(t, new PermissionForm("접근셋", null, PermissionStatus.ACTIVE));
    permissionService.addEntry(t, p.getId(), srv.getId(), PermissionProtocol.SSH);
    permissionService.assignToGroup(t, p.getId(), dev.getId());

    mockMvc.perform(get("/portal").with(authentication(auth(staff))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("권한서버")));
  }

  @Test
  void deputyInheritsAbsentServerInPortal() throws Exception {
    var t = tenant();
    ManagedUser absent = user(t);
    ManagedUser deputy = user(t);
    AccessGroup team = group(t);
    groupService.addMember(t, team.getId(), absent.getId()); // deputy는 팀 밖(상속으로만 접근)
    Asset srv = server(t, "부재서버");
    assetService.assignOwnerGroup(t, srv.getId(), team.getId());
    deputyService.create(t, absent.getId(), deputy.getId(),
        LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), null);

    mockMvc.perform(get("/portal").with(authentication(auth(deputy))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("부재서버")));
  }

  private UUID tenant() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("포털사", "PORT" + System.nanoTime()));
    tenantService.updateDetail(tenant.getId(), null, null, Set.of(FeatureModule.SERVER_ACCESS));
    return tenant.getId();
  }

  private Asset server(UUID t, String name) {
    return assetService.create(t, new AssetForm(
        name + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "10.0.0.1", 22, "",
        "LINUX", "", AssetStatus.ACTIVE));
  }

  private AccessGroup group(UUID t) {
    return groupService.create(t,
        new AccessGroupForm("개발팀-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
  }

  private ManagedUser user(UUID t) {
    long n = System.nanoTime();
    return userService.create(t,
        new UserForm("사원" + n, "사원", "p" + n + "@test.com", "010-1234-5678", "safe-password-123", UserStatus.ACTIVE),
        Set.of());
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(
        principal, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }
}
