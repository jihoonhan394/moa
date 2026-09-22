package com.moara.moa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.ai.AiProvider;
import com.moara.moa.ai.AiService;
import com.moara.moa.ai.AiSetting;
import com.moara.moa.ai.AiSettingForm;
import com.moara.moa.ai.AiSettingService;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** AI 설정: 키 볼트 암호화 왕복·빈 값 유지·활성 판정·역할 게이팅. 실제 API 호출은 테스트하지 않음. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiSettingFlowTest {
  private static final UUID MOA = Tenant.DEFAULT_TENANT_ID;

  @Autowired private MockMvc mockMvc;
  @Autowired private AiSettingService settingService;
  @Autowired private AiService aiService;
  @Autowired private ManagedUserService userService;

  @Test
  void keyRoundtripAndBlankKeepsExisting() {
    settingService.saveForTenant(MOA, new AiSettingForm(AiProvider.DEEPSEEK, null, null, "test-key-abc", true));
    AiSetting setting = settingService.findForTenant(MOA).orElseThrow();
    assertTrue(setting.hasSecret());
    assertEquals("test-key-abc", settingService.decryptApiKey(setting));

    // 빈 키로 다시 저장 → 기존 키 유지.
    settingService.saveForTenant(MOA, new AiSettingForm(AiProvider.OPENAI, "gpt-4o-mini", null, "", true));
    AiSetting updated = settingService.findForTenant(MOA).orElseThrow();
    assertEquals(AiProvider.OPENAI, updated.getProvider());
    assertEquals("test-key-abc", settingService.decryptApiKey(updated));
  }

  @Test
  void isConfiguredNeedsEnabledAndKey() {
    settingService.saveForTenant(MOA, new AiSettingForm(AiProvider.DEEPSEEK, null, null, "k", false));
    assertFalse(aiService.isConfigured(MOA), "비활성이면 미설정 취급");
    settingService.saveForTenant(MOA, new AiSettingForm(AiProvider.DEEPSEEK, null, null, "", true));
    assertTrue(aiService.isConfigured(MOA), "활성 + 키 있으면 설정됨");
  }

  @Test
  void webGatedToTenantAdmin() throws Exception {
    ManagedUser u = user();
    mockMvc.perform(get("/ai-settings").with(authentication(auth(u, "ROLE_TENANT_ADMIN"))))
        .andExpect(status().isOk());
    mockMvc.perform(get("/ai-settings").with(authentication(auth(u, "ROLE_USER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void tenantAdminSavesViaPost() throws Exception {
    ManagedUser admin = user();
    mockMvc.perform(post("/ai-settings").with(authentication(auth(admin, "ROLE_TENANT_ADMIN"))).with(csrf())
            .param("provider", "DEEPSEEK").param("apiKey", "web-key-1").param("enabled", "true"))
        .andExpect(status().is3xxRedirection());
    assertTrue(settingService.findForTenant(MOA).orElseThrow().hasSecret());
  }

  private ManagedUser user() {
    String username = "u" + System.nanoTime();
    return userService.create(new UserForm(
        username, "관리자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user, String role) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", List.of(new SimpleGrantedAuthority(role)));
  }
}
