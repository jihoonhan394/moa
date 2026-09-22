package com.moara.moa.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
class PortalControllerTest {
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

  @Test
  void rendersAccessibleAssetsForLoggedInUser() throws Exception {
    ManagedUser user = createMoaUser();
    AccessGroup group = groupService.create(user.getTenantId(),
        new AccessGroupForm("group-" + System.nanoTime(), "설명", AccessGroupStatus.ACTIVE, null));
    String assetName = "portal-asset-" + System.nanoTime();
    Asset asset = assetService.create(user.getTenantId(), new AssetForm(
        assetName, AssetType.SERVER, AssetProtocol.SSH, "192.0.2.50", 22, "", "LINUX", "t", AssetStatus.ACTIVE));
    grantAccess(user.getTenantId(), group.getId(), asset.getId(), PermissionProtocol.SSH);
    groupService.addMember(user.getTenantId(), group.getId(), user.getId());

    mockMvc.perform(get("/portal").with(authentication(auth(user))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(assetName)));
  }

  @Test
  void showsEmptyStateWhenUserHasNoAccess() throws Exception {
    ManagedUser user = createMoaUser();

    mockMvc.perform(get("/portal").with(authentication(auth(user))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("접속 가능한 자산이 없습니다")));
  }

  @Test
  void doesNotShowOtherUsersAccessibleAsset() throws Exception {
    // 권한이 부여된 사용자
    ManagedUser owner = createMoaUser();
    AccessGroup group = groupService.create(owner.getTenantId(),
        new AccessGroupForm("group-" + System.nanoTime(), "설명", AccessGroupStatus.ACTIVE, null));
    String assetName = "secret-asset-" + System.nanoTime();
    Asset asset = assetService.create(owner.getTenantId(), new AssetForm(
        assetName, AssetType.SERVER, AssetProtocol.SSH, "192.0.2.51", 22, "", "LINUX", "t", AssetStatus.ACTIVE));
    grantAccess(owner.getTenantId(), group.getId(), asset.getId(), PermissionProtocol.SSH);
    groupService.addMember(owner.getTenantId(), group.getId(), owner.getId());

    // 권한이 없는 다른 사용자에게는 보이지 않는다.
    ManagedUser stranger = createMoaUser();
    mockMvc.perform(get("/portal").with(authentication(auth(stranger))))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString(assetName))));
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
