package com.moara.moa.web;

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
import com.moara.moa.connection.ConnectionSessionService;
import com.moara.moa.connection.SessionProtocol;
import com.moara.moa.security.MoaUserDetails;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 접속 이력: 기관 관리자만 열람(USER 403), 기록된 세션의 사용자명이 목록에 노출되는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessHistoryTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;
  @Autowired private AssetService assetService;
  @Autowired private ConnectionSessionService sessionService;

  @Test
  void adminSeesTenantConnectionHistoryUserForbidden() throws Exception {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("접속사", "CONN" + System.nanoTime()));
    ManagedUser admin = userService.createTenantAdmin(
        tenant.getId(), "ta" + System.nanoTime(), "관리자",
        "adm" + System.nanoTime() + "@example.com", "010-0000-0000", "safe-password-123");
    String uname = "u" + System.nanoTime();
    ManagedUser user = userService.create(tenant.getId(),
        new UserForm(uname, "접속유저", uname + "@example.com", "safe-password-123", UserStatus.ACTIVE));

    Asset asset = assetService.create(tenant.getId(), new AssetForm(
        "웹서버01", AssetType.SERVER, AssetProtocol.SSH, "10.0.0.5", 22, null, "LINUX", null, AssetStatus.ACTIVE));

    // 접속 세션 1건 기록.
    sessionService.openSession(
        tenant.getId(), user.getId(), asset.getId(), SessionProtocol.SSH, "sess-" + System.nanoTime(), "10.0.0.9");

    // 관리자: 200 + 사용자 아이디·자산명이 목록에 노출.
    mockMvc.perform(get("/access-history").with(authentication(auth(admin))))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString(uname)))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("웹서버01")));

    // 일반 사용자: 접근 차단(403).
    mockMvc.perform(get("/access-history").with(authentication(auth(user))))
        .andExpect(status().isForbidden());
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
  }
}
