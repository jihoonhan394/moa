package com.moara.moa.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 인벤토리는 자산 관리자(ASSET_MANAGER) 전용 — 인프라 관리자·일반 사용자는 차단. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryFlowTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ManagedUserService userService;

  @Test
  void assetManagerCanListAndCreate() throws Exception {
    ManagedUser mgr = user();
    String name = "노트북-" + System.nanoTime();

    mockMvc.perform(post("/inventory").with(authentication(auth(mgr, "ROLE_ASSET_MANAGER"))).with(csrf())
            .param("name", name).param("category", "실물 / 사무기기 / 컴퓨터"))
        .andExpect(status().is3xxRedirection());

    mockMvc.perform(get("/inventory").with(authentication(auth(mgr, "ROLE_ASSET_MANAGER"))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(name)));
  }

  @Test
  void infraManagerAndUserAreForbidden() throws Exception {
    ManagedUser mgr = user();
    mockMvc.perform(get("/inventory").with(authentication(auth(mgr, "ROLE_INFRA_MANAGER"))))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/inventory").with(authentication(auth(mgr, "ROLE_USER"))))
        .andExpect(status().isForbidden());
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
