package com.moara.moa.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetForm;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetStatus;
import com.moara.moa.asset.AssetType;
import com.moara.moa.connection.ConnectionSession;
import com.moara.moa.connection.ConnectionSessionService;
import com.moara.moa.connection.ConnectionStatus;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupForm;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.group.AccessGroupStatus;
import com.moara.moa.guacamole.GuacamoleClient;
import com.moara.moa.guacamole.GuacamoleConnectionRequest;
import com.moara.moa.guacamole.GuacamoleLaunch;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConnectionControllerTest {
  static final String STUB_URL = "http://guac/#/client/abc?token=T";

  @TestConfiguration
  static class StubGuacamoleConfig {
    @Bean
    @Primary
    GuacamoleClient stubGuacamoleClient() {
      return new GuacamoleClient() {
        @Override public boolean isEnabled() { return true; }
        @Override public GuacamoleLaunch createSession(GuacamoleConnectionRequest request) {
          return new GuacamoleLaunch(STUB_URL);
        }
      };
    }
  }

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
  @Autowired private ConnectionSessionService sessionService;

  @Test
  void connectFormRendersForAccessibleServer() throws Exception {
    ManagedUser user = createMoaUser();
    Asset server = grantServerTo(user);

    mockMvc.perform(get("/portal/assets/" + server.getId() + "/connect").with(authentication(auth(user))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(server.getHost())));
  }

  @Test
  void connectFormOpensConsoleInPopupWindow() throws Exception {
    ManagedUser user = createMoaUser();
    Asset server = grantServerTo(user);

    mockMvc.perform(get("/portal/assets/" + server.getId() + "/connect").with(authentication(auth(user))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("target=\"moaConsole\"")))
        .andExpect(content().string(containsString("window.open('', 'moaConsole'")));
  }

  @Test
  void connectRedirectsToGatewayAndRecordsConnectedSession() throws Exception {
    ManagedUser user = createMoaUser();
    Asset server = grantServerTo(user);

    mockMvc.perform(post("/portal/assets/" + server.getId() + "/connect")
            .with(authentication(auth(user))).with(csrf())
            .param("username", "mtcm").param("password", "secret"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl(STUB_URL));

    List<ConnectionSession> sessions = sessionService.findHistoryForUser(user.getTenantId(), user.getId());
    assertTrue(sessions.stream().anyMatch(s ->
        s.getAssetId().equals(server.getId()) && s.getStatus() == ConnectionStatus.CONNECTED));
  }

  @Test
  void connectDeniedForUserWithoutAccess() throws Exception {
    ManagedUser owner = createMoaUser();
    Asset server = grantServerTo(owner);
    ManagedUser stranger = createMoaUser();

    mockMvc.perform(post("/portal/assets/" + server.getId() + "/connect")
            .with(authentication(auth(stranger))).with(csrf())
            .param("username", "x").param("password", "y"))
        .andExpect(redirectedUrl("/portal?error=denied"));

    // 거부된 시도도 FAILED로 기록된다.
    List<ConnectionSession> sessions = sessionService.findHistoryForUser(stranger.getTenantId(), stranger.getId());
    assertEquals(1, sessions.size());
    assertEquals(ConnectionStatus.FAILED, sessions.get(0).getStatus());
  }

  private Asset grantServerTo(ManagedUser user) {
    AccessGroup group = groupService.create(user.getTenantId(),
        new AccessGroupForm("grp-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
    Asset server = assetService.create(user.getTenantId(), new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "192.0.2.80", 22, "", "LINUX", "t", AssetStatus.ACTIVE));
    grantAccess(user.getTenantId(), group.getId(), server.getId(), PermissionProtocol.SSH);
    groupService.addMember(user.getTenantId(), group.getId(), user.getId());
    return server;
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
