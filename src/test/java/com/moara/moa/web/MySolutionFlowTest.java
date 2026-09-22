package com.moara.moa.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetForm;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetStatus;
import com.moara.moa.asset.AssetType;
import com.moara.moa.remote.ExecResult;
import com.moara.moa.remote.RemoteExecutor;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.solution.ManagedSolution;
import com.moara.moa.solution.SolutionForm;
import com.moara.moa.solution.SolutionStatus;
import com.moara.moa.solution.SolutionType;
import com.moara.moa.solution.ManagedSolutionService;
import com.moara.moa.tenant.Tenant;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** A작업: 자원 관리자가 솔루션을 사용자에게 배정하고, 배정받은 일반 사용자만 제어하는 흐름. */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MySolutionFlowTest {
  private static final java.util.UUID TENANT = Tenant.DEFAULT_TENANT_ID;

  @TestConfiguration
  static class StubConfig {
    @Bean(name = "remoteExecutorDispatcher")
    @Primary
    RemoteExecutor stubExecutor() {
      return (target, command) -> new ExecResult(0, "active");
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ManagedSolutionService solutionService;
  @Autowired private AssetService assetService;
  @Autowired private ManagedUserService userService;

  @Test
  void assignedUserSeesAndControlsSolution() throws Exception {
    ManagedSolution solution = solution();
    ManagedUser worker = user("일반사용자");

    // 자원 관리자가 배정.
    mockMvc.perform(post("/solutions/" + solution.getId() + "/users")
            .with(authentication(auth(assetManager(), "ROLE_INFRA_MANAGER"))).with(csrf())
            .param("userId", worker.getId().toString()))
        .andExpect(status().is3xxRedirection());

    // 배정받은 사용자 화면에 노출.
    mockMvc.perform(get("/my-solutions").with(authentication(auth(worker, "ROLE_USER"))))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString(solution.getName())));

    // 배정받은 사용자가 제어 → 성공(리다이렉트).
    mockMvc.perform(post("/my-solutions/" + solution.getId() + "/control")
            .with(authentication(auth(worker, "ROLE_USER"))).with(csrf()).param("action", "STATUS"))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void unassignedUserCannotControl() throws Exception {
    ManagedSolution solution = solution();
    ManagedUser stranger = user("미배정자");

    mockMvc.perform(post("/my-solutions/" + solution.getId() + "/control")
            .with(authentication(auth(stranger, "ROLE_USER"))).with(csrf()).param("action", "STATUS"))
        .andExpect(status().isForbidden());
  }

  private ManagedSolution solution() {
    Asset server = assetService.create(TENANT, new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "100.85.241.103", 22, "", "LINUX", "t",
        AssetStatus.ACTIVE));
    String name = "sol-" + System.nanoTime();
    return solutionService.create(TENANT, new SolutionForm(
        server.getId(), name, SolutionType.CUSTOM_COMMAND, name, null,
        com.moara.moa.solution.HealthCheckType.NONE, null, SolutionStatus.ACTIVE,
        "bash start.sh", "bash stop.sh", "bash status.sh",
        com.moara.moa.remote.RemoteProtocol.SSH, null));
  }

  private ManagedUser user(String name) {
    String username = "u" + System.nanoTime();
    return userService.create(new UserForm(
        username, name, username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private ManagedUser assetManager() {
    return user("자원관리자");
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user, String role) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", List.of(new SimpleGrantedAuthority(role)));
  }
}
