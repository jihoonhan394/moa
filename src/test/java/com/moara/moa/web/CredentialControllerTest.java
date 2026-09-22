package com.moara.moa.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.credential.CredentialService;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.Tenant;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CredentialControllerTest {
  private static final UUID TENANT = Tenant.DEFAULT_TENANT_ID;

  @Autowired private MockMvc mockMvc;
  @Autowired private CredentialService credentialService;
  @Autowired private ManagedUserService userService;

  @Test
  void listRendersForAdmin() throws Exception {
    mockMvc.perform(get("/credentials").with(authentication(auth(admin()))))
        .andExpect(status().isOk());
  }

  @Test
  void createStoresCredential() throws Exception {
    String name = "cred-" + System.nanoTime();
    mockMvc.perform(post("/credentials").with(authentication(auth(admin()))).with(csrf())
            .param("name", name).param("type", "PASSWORD").param("username", "mtcm").param("secret", "test-secret-1"))
        .andExpect(status().is3xxRedirection());
    assertTrue(credentialService.findAll(TENANT).stream().anyMatch(c -> c.getName().equals(name)));
  }

  private ManagedUser admin() {
    String username = "user" + System.nanoTime();
    return userService.create(new UserForm(
        username, "관리자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "",
        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INFRA_MANAGER")));
  }
}
