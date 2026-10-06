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
import com.moara.moa.credential.Credential;
import com.moara.moa.credential.CredentialForm;
import com.moara.moa.credential.CredentialService;
import com.moara.moa.credential.CredentialType;
import com.moara.moa.remote.ExecResult;
import com.moara.moa.remote.RemoteExecutor;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.remote.RemoteProtocol;
import com.moara.moa.solution.HealthCheckType;
import com.moara.moa.solution.ManagedSolution;
import com.moara.moa.solution.ManagedSolutionService;
import com.moara.moa.solution.SolutionForm;
import com.moara.moa.solution.SolutionStatus;
import com.moara.moa.solution.SolutionType;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
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

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SolutionControllerTest {
  private static final UUID TENANT = Tenant.DEFAULT_TENANT_ID;

  @TestConfiguration
  static class StubConfig {
    // 실행기 디스패처(@Primary)를 스텁으로 대체(실제 원격 실행 방지).
    @Bean(name = "remoteExecutorDispatcher")
    @Primary
    RemoteExecutor stubExecutor() {
      return (target, command) -> new ExecResult(0, "active");
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ManagedSolutionService solutionService;
  @Autowired private AssetService assetService;
  @Autowired private CredentialService credentialService;
  @Autowired private ManagedUserService userService;

  @Test
  void listRendersForAdmin() throws Exception {
    mockMvc.perform(get("/solutions").with(authentication(auth(admin()))))
        .andExpect(status().isOk());
  }

  @Test
  void createSolutionThenControlIt() throws Exception {
    ManagedUser admin = admin();
    Asset server = assetService.create(TENANT, new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "100.85.241.103", 22, "", "LINUX", "t",
        AssetStatus.ACTIVE));
    Credential credential = credentialService.create(TENANT, new CredentialForm(
        "c-" + System.nanoTime(), CredentialType.PASSWORD, "mtcm", "test-secret-1"));
    String name = "sol-" + System.nanoTime();

    mockMvc.perform(post("/solutions").with(authentication(auth(admin))).with(csrf())
            .param("assetId", server.getId().toString())
            .param("name", name)
            .param("type", "CUSTOM_COMMAND")
            .param("identifier", name)
            .param("credentialId", credential.getId().toString())
            .param("healthCheckType", "NONE")
            .param("status", "ACTIVE")
            .param("startCommand", "bash start.sh")
            .param("stopCommand", "bash stop.sh")
            .param("statusCommand", "bash status.sh"))
        .andExpect(status().is3xxRedirection());

    List<ManagedSolution> created = solutionService.findByAsset(TENANT, server.getId());
    assertEquals(1, created.size());

    mockMvc.perform(post("/solutions/" + created.get(0).getId() + "/control")
            .with(authentication(auth(admin))).with(csrf()).param("action", "STATUS"))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  @DisplayName("⚠️ 점검 방식이 NONE 인 솔루션의 행도 칸 수가 같다 — 하나 비면 그 행만 열이 밀린다")
  void NONE_솔루션도_표_칸이_비지_않는다() throws Exception {
    ManagedUser admin = admin();
    Asset server = assetService.create(TENANT, new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "127.0.0.1", 22, "",
        "LINUX", "t", AssetStatus.ACTIVE));
    String name = "none-" + System.nanoTime();
    solutionService.create(TENANT, new SolutionForm(
        server.getId(), name, SolutionType.LINUX_DAEMON, name + ".service", null,
        HealthCheckType.NONE, null, SolutionStatus.ACTIVE, null, null, null,
        RemoteProtocol.SSH, null));

    String html = mockMvc.perform(get("/solutions").with(authentication(auth(admin))))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    // .table-row 는 행마다 독립 grid 라 칸 수가 머리와 같아야 정렬이 맞는다.
    // NONE 이면 배지가 없으므로 자리표시(—)가 그 칸을 채운다.
    int headStart = html.indexOf("table-row table-head");
    assertTrue(headStart > 0, "표 머리가 없다");
    String head = html.substring(headStart, html.indexOf("</div>", headStart));
    assertEquals(6, countOccurrences(head, "<span>"), "표 머리 칸 수: " + head);

    String row = rowContaining(html, name);
    assertTrue(row.contains("—") || row.contains("&mdash;"),
        "NONE 행에 자리표시가 없다 — 칸이 비면 뒤 열이 밀린다: " + row);
  }

  /** 그 이름이 들어 있는 표 행. */
  private static String rowContaining(String html, String name) {
    int at = html.indexOf(name);
    assertTrue(at > 0, "목록에 " + name + " 이 없다");
    int from = html.lastIndexOf("<div class=\"table-row\">", at);
    return html.substring(from < 0 ? Math.max(0, at - 300) : from, Math.min(html.length(), at + 900));
  }

  private static int countOccurrences(String text, String needle) {
    int count = 0;
    for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
      count++;
    }
    return count;
  }

  private ManagedUser admin() {
    String username = "user" + System.nanoTime();
    return userService.create(new UserForm(
        username, "관리자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "",
        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INFRA_MANAGER")));
  }
}
