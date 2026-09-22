package com.moara.moa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.moara.moa.permission.PermissionSetService;
import com.moara.moa.permission.PermissionStatus;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermissionControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private PermissionSetService permissionService;
  @Autowired private AccessGroupService groupService;
  @Autowired private AssetService assetService;
  @Autowired private ManagedUserService userService;

  @Test
  void listAndCreatePermission() throws Exception {
    ManagedUser admin = createUser();
    mockMvc.perform(get("/permissions").with(authentication(auth(admin))))
        .andExpect(status().isOk());

    String name = "perm-" + System.nanoTime();
    mockMvc.perform(post("/permissions").with(authentication(auth(admin))).with(csrf())
            .param("name", name).param("description", "설명").param("status", "ACTIVE"))
        .andExpect(status().is3xxRedirection());
    assertTrue(permissionService.findAll(admin.getTenantId()).stream()
        .anyMatch(p -> p.getName().equals(name)));
  }

  @Test
  void addEntryAndAssignGroupAndUser() throws Exception {
    ManagedUser admin = createUser();
    Permission permission = permissionService.create(admin.getTenantId(),
        new PermissionForm("p-" + System.nanoTime(), null, PermissionStatus.ACTIVE));
    Asset asset = createServer(admin);
    AccessGroup group = createGroup(admin);
    String base = "/permissions/" + permission.getId();

    mockMvc.perform(post(base + "/entries").with(authentication(auth(admin))).with(csrf())
            .param("assetId", asset.getId().toString()).param("action", "SSH"))
        .andExpect(status().is3xxRedirection());
    assertEquals(1, permissionService.findEntries(admin.getTenantId(), permission.getId()).size());

    mockMvc.perform(post(base + "/groups").with(authentication(auth(admin))).with(csrf())
            .param("groupId", group.getId().toString()))
        .andExpect(status().is3xxRedirection());
    assertEquals(1, permissionService.findGroupAssignments(admin.getTenantId(), permission.getId()).size());

    mockMvc.perform(post(base + "/users").with(authentication(auth(admin))).with(csrf())
            .param("userId", admin.getId().toString()).param("expiresAt", ""))
        .andExpect(status().is3xxRedirection());
    assertEquals(1, permissionService.findUserAssignments(admin.getTenantId(), permission.getId()).size());
  }

  private AccessGroup createGroup(ManagedUser owner) {
    return groupService.create(owner.getTenantId(),
        new AccessGroupForm("grp-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
  }

  private Asset createServer(ManagedUser owner) {
    return assetService.create(owner.getTenantId(), new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "192.0.2.82", 22, "", "LINUX", "t",
        AssetStatus.ACTIVE));
  }

  private ManagedUser createUser() {
    String username = "user" + System.nanoTime();
    return userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "",
        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INFRA_MANAGER")));
  }
}
