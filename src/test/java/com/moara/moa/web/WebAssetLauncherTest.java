package com.moara.moa.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetForm;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetStatus;
import com.moara.moa.asset.AssetType;
import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
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
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebAssetLauncherTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private AccessGroupService groupService;
  @Autowired private AssetService assetService;
  @Autowired private PermissionSetService permissionSetService;

  private void grantAccess(
      java.util.UUID tenantId, java.util.UUID groupId, java.util.UUID assetId, PermissionProtocol action) {
    Permission permission = permissionSetService.create(
        tenantId, new PermissionForm("p-" + System.nanoTime(), null, PermissionStatus.ACTIVE));
    permissionSetService.addEntry(tenantId, permission.getId(), assetId, action);
    permissionSetService.assignToGroup(tenantId, permission.getId(), groupId);
  }
  @Autowired private ManagedUserService userService;
  @Autowired private AuditLogService auditLogService;

  @Test
  void opensWebAssetAndRecordsHistory() throws Exception {
    ManagedUser user = createMoaUser();
    Asset web = grantWebAssetTo(user, "https://example.com/portal");

    mockMvc.perform(get("/portal/assets/" + web.getId() + "/open").with(authentication(auth(user))))
        .andExpect(redirectedUrl("https://example.com/portal"));

    assertTrue(auditLogService.findByActor(user.getId()).stream()
        .anyMatch(log -> log.getAction().equals("WEB_ASSET_OPEN")
            && log.getResult() == AuditResult.SUCCESS
            && log.getTargetId().equals(web.getId())));
  }

  @Test
  void deniedWhenUserHasNoAccess() throws Exception {
    ManagedUser owner = createMoaUser();
    Asset web = grantWebAssetTo(owner, "https://example.com/secret");
    ManagedUser stranger = createMoaUser();

    mockMvc.perform(get("/portal/assets/" + web.getId() + "/open").with(authentication(auth(stranger))))
        .andExpect(redirectedUrl("/portal?error=denied"));

    assertTrue(auditLogService.findByActor(stranger.getId()).stream()
        .anyMatch(log -> log.getAction().equals("WEB_ASSET_OPEN") && log.getResult() == AuditResult.FAILURE));
  }

  @Test
  void rejectsNonWebAsset() throws Exception {
    ManagedUser user = createMoaUser();
    AccessGroup group = groupService.create(user.getTenantId(),
        new AccessGroupForm("grp-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
    Asset server = assetService.create(user.getTenantId(), new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "192.0.2.70", 22, "", "LINUX", "t", AssetStatus.ACTIVE));
    grantAccess(user.getTenantId(), group.getId(), server.getId(), PermissionProtocol.SSH);
    groupService.addMember(user.getTenantId(), group.getId(), user.getId());

    mockMvc.perform(get("/portal/assets/" + server.getId() + "/open").with(authentication(auth(user))))
        .andExpect(redirectedUrl("/portal?error=invalid"));
  }

  private Asset grantWebAssetTo(ManagedUser user, String url) {
    AccessGroup group = groupService.create(user.getTenantId(),
        new AccessGroupForm("grp-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
    Asset web = assetService.create(user.getTenantId(), new AssetForm(
        "web-" + System.nanoTime(), AssetType.WEBSITE, AssetProtocol.HTTPS, "", null, url, "", "t", AssetStatus.ACTIVE));
    grantAccess(user.getTenantId(), group.getId(), web.getId(), PermissionProtocol.HTTPS);
    groupService.addMember(user.getTenantId(), group.getId(), user.getId());
    return web;
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
  }

  private ManagedUser createMoaUser() {
    String username = "user" + System.nanoTime();
    return userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
