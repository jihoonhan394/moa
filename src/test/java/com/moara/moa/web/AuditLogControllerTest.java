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
class AuditLogControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ManagedUserService userService;

  @Test
  void groupCreationIsRecordedAndVisibleInAuditLog() throws Exception {
    ManagedUser actor = createMoaUser();
    String groupName = "감사그룹-" + System.nanoTime();

    mockMvc.perform(post("/groups").with(authentication(auth(actor))).with(csrf())
            .param("name", groupName).param("description", ""))
        .andExpect(status().is3xxRedirection());

    mockMvc.perform(get("/audit").with(authentication(auth(actor))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("GROUP_CREATE")))
        .andExpect(content().string(containsString(actor.getName())));
  }

  @Test
  void userCreationIsRecordedInAuditLog() throws Exception {
    ManagedUser actor = createMoaUser();
    String username = "audited" + System.nanoTime();

    mockMvc.perform(post("/users").with(authentication(auth(actor))).with(csrf())
            .param("username", username).param("name", "감사대상")
            .param("email", username + "@example.com").param("phone", "010-1234-5678")
            .param("password", "safe-password-123").param("passwordConfirm", "safe-password-123")
            .param("status", "ACTIVE"))
        .andExpect(status().is3xxRedirection());

    mockMvc.perform(get("/audit").with(authentication(auth(actor))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("USER_CREATE")));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "",
        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
  }

  private ManagedUser createMoaUser() {
    String username = "user" + System.nanoTime();
    return userService.create(new UserForm(
        username, "감사행위자" + System.nanoTime(), username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
