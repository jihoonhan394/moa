package com.moara.moa.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetService;
import com.moara.moa.audit.AuditLogService;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 자산 변경 이력: 생성·수정이 감사 로그로 남고, 변경 요약(diff)이 이력 화면에 보인다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssetHistoryFlowTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ManagedUserService userService;
  @Autowired private AssetService assetService;
  @Autowired private AuditLogService auditLogService;

  @Test
  void registeredServerAndAssetAppearInLists() throws Exception {
    ManagedUser mgr = user();
    var authn = auth(mgr);
    String name = "reg-srv-" + System.nanoTime();
    mockMvc.perform(post("/servers").with(authentication(authn)).with(csrf())
            .param("name", name).param("assetType", "SERVER").param("protocol", "SSH")
            .param("host", "10.0.0.7").param("port", "22").param("url", "")
            .param("osType", "LINUX").param("description", "").param("status", "ACTIVE"))
        .andExpect(status().is3xxRedirection());

    // 등록한 서버가 /servers·/assets 목록에 실제로 나타난다(하드코딩 아님).
    mockMvc.perform(get("/servers").with(authentication(authn)))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString(name)));
    mockMvc.perform(get("/assets").with(authentication(authn)))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString(name)));
    // 서버 현황도 목업이 아니라 실제 등록 서버를 보여준다.
    mockMvc.perform(get("/server-status").with(authentication(authn)))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString(name)));
  }

  @Test
  void createAndUpdateAreAuditedAndShownInHistory() throws Exception {
    ManagedUser mgr = user();
    var authn = auth(mgr);
    String name = "srv-" + System.nanoTime();

    mockMvc.perform(post("/assets").with(authentication(authn)).with(csrf())
            .param("name", name).param("assetType", "SERVER").param("protocol", "SSH")
            .param("host", "192.0.2.10").param("port", "22").param("url", "")
            .param("osType", "LINUX").param("description", "초기").param("status", "ACTIVE"))
        .andExpect(status().is3xxRedirection());

    Asset created = assetService.findAll(mgr.getTenantId()).stream()
        .filter(a -> a.getName().equals(name)).findFirst().orElseThrow();

    // 상태·설명 변경.
    mockMvc.perform(post("/assets/" + created.getId()).with(authentication(authn)).with(csrf())
            .param("name", name).param("assetType", "SERVER").param("protocol", "SSH")
            .param("host", "192.0.2.10").param("port", "22").param("url", "")
            .param("osType", "LINUX").param("description", "변경됨").param("status", "DISABLED"))
        .andExpect(status().is3xxRedirection());

    mockMvc.perform(get("/assets/" + created.getId() + "/history").with(authentication(authn)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("생성")))
        .andExpect(content().string(containsString("상태")))       // diff 라벨
        .andExpect(content().string(containsString("변경됨")));      // 설명 신규값

    Assertions.assertThat(auditLogService.findByTarget(mgr.getTenantId(), "Asset", created.getId()))
        .hasSize(2)
        .anyMatch(l -> l.getAction().equals("ASSET_CREATE"))
        .anyMatch(l -> l.getAction().equals("ASSET_UPDATE") && l.getMessage().contains("상태"));
  }

  private ManagedUser user() {
    String username = "u" + System.nanoTime();
    return userService.create(new UserForm(
        username, "관리자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(
        principal, "", List.of(new SimpleGrantedAuthority("ROLE_INFRA_MANAGER")));
  }
}
