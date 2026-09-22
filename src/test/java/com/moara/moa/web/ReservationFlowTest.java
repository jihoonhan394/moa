package com.moara.moa.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.reservation.SharedResource;
import com.moara.moa.reservation.SharedResourceForm;
import com.moara.moa.reservation.SharedResourceService;
import com.moara.moa.reservation.SharedResourceStatus;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.Tenant;
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

/** 공유자산 등록은 자산 관리자 전용, 예약은 일반 사용자도 가능. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationFlowTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ManagedUserService userService;
  @Autowired private SharedResourceService resourceService;

  @Test
  void sharedResourcesAssetManagerOnly() throws Exception {
    ManagedUser u = user();
    mockMvc.perform(get("/shared-resources").with(authentication(auth(u, "ROLE_ASSET_MANAGER"))))
        .andExpect(status().isOk());
    mockMvc.perform(get("/shared-resources").with(authentication(auth(u, "ROLE_USER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void userCanViewAndBook() throws Exception {
    SharedResource room = resourceService.create(Tenant.DEFAULT_TENANT_ID, new SharedResourceForm(
        "회의실-" + System.nanoTime(), "회의실", "3층", 6, SharedResourceStatus.ACTIVE, "설명"));
    ManagedUser u = user();

    mockMvc.perform(get("/reservations").with(authentication(auth(u, "ROLE_USER"))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(room.getName())));

    mockMvc.perform(post("/reservations").with(authentication(auth(u, "ROLE_USER"))).with(csrf())
            .param("resourceId", room.getId().toString())
            .param("startsAt", "2030-02-01T10:00")
            .param("endsAt", "2030-02-01T11:00")
            .param("purpose", "주간회의"))
        .andExpect(status().is3xxRedirection());
  }

  private ManagedUser user() {
    String username = "u" + System.nanoTime();
    return userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user, String role) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", List.of(new SimpleGrantedAuthority(role)));
  }
}
