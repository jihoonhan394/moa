package com.moara.moa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.moara.moa.audit.AuditLogService;
import com.moara.moa.remote.RemoteChannel;
import com.moara.moa.remote.RemoteHostKeyStore;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 호스트 신원 기록을 사람이 확인하고 지우는 길. <b>이 문이 없으면 서버를 다시 깐 순간 제어가
 * 영영 막힌다</b> — TOFU를 켜면서 같이 만들어야 하는 출구다.
 *
 * <p>반대로 아무나 지울 수 있으면 검증이 무의미해진다. 중간자 공격의 마지막 단계가 기록을
 * 지우는 것이므로, 누가 언제 지웠는지 감사에 남는지도 함께 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HostKeyForgetFlowTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ManagedUserService userService;
  @Autowired private AssetService assetService;
  @Autowired private RemoteHostKeyStore hostKeyStore;
  @Autowired private AuditLogService auditLogService;

  @Test
  void managerSeesRecordedIdentityThenForgetsIt() throws Exception {
    ManagedUser manager = withRole(UserRole.INFRA_MANAGER);
    Asset server = server();
    hostKeyStore.verify(Tenant.DEFAULT_TENANT_ID, server.getHost(), server.getPort(),
        RemoteChannel.SSH, "ssh-ed25519", "SHA256:recorded-fingerprint");

    // 상세 화면에 지문이 보인다 — 서버에서 본 값과 눈으로 대조할 수 있어야 한다.
    mockMvc.perform(get("/servers/" + server.getId()).with(authentication(auth(manager))))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("SHA256:recorded-fingerprint")))
        .andExpect(content().string(Matchers.containsString("원격 신원")));

    mockMvc.perform(post("/servers/" + server.getId() + "/host-key/forget")
            .with(authentication(auth(manager))).with(csrf()))
        .andExpect(status().is3xxRedirection());

    assertTrue(hostKeyStore
        .find(Tenant.DEFAULT_TENANT_ID, server.getHost(), server.getPort(), RemoteChannel.SSH)
        .isEmpty(), "기록이 지워지지 않았다");
  }

  /** 지운 사실은 감사에 남는다 — 공격의 마지막 단계가 될 수 있는 행위다. */
  @Test
  void forgettingIsAudited() throws Exception {
    ManagedUser manager = withRole(UserRole.INFRA_MANAGER);
    Asset server = server();
    hostKeyStore.verify(Tenant.DEFAULT_TENANT_ID, server.getHost(), server.getPort(),
        RemoteChannel.SSH, "ssh-ed25519", "SHA256:whatever");

    mockMvc.perform(post("/servers/" + server.getId() + "/host-key/forget")
        .with(authentication(auth(manager))).with(csrf()));

    boolean recorded = auditLogService
        .findByTarget(Tenant.DEFAULT_TENANT_ID, "Asset", server.getId()).stream()
        .anyMatch(log -> "ASSET_FORGET_HOST_KEY".equals(log.getAction()));
    assertTrue(recorded, "호스트 신원 삭제가 감사에 남지 않았다");
  }

  /** 일반 사용자는 지울 수 없다. 지울 수 있으면 검증이 있으나 마나다. */
  @Test
  void plainUserCannotForget() throws Exception {
    Asset server = server();

    mockMvc.perform(post("/servers/" + server.getId() + "/host-key/forget")
            .with(authentication(auth(user()))).with(csrf()))
        .andExpect(status().isForbidden());
  }

  /** 기록이 없으면 "없다"고 말한다 — 빈 화면이 다음 할 일을 알려줘야 한다. */
  @Test
  void detailExplainsWhenNothingRecordedYet() throws Exception {
    Asset server = server();

    mockMvc.perform(get("/servers/" + server.getId())
            .with(authentication(auth(withRole(UserRole.INFRA_MANAGER)))))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("아직 기록이 없습니다")));
  }

  // ── 도우미 ────────────────────────────────────────────────────────────────

  private Asset server() {
    String name = "srv-" + System.nanoTime();
    Asset created = assetService.create(Tenant.DEFAULT_TENANT_ID, new AssetForm(
        name, AssetType.SERVER, AssetProtocol.SSH, "10.20.30.40", 22, "",
        "LINUX", "신원 확인용 서버", AssetStatus.ACTIVE));
    assertEquals(name, created.getName());
    return created;
  }

  private ManagedUser user() {
    String username = "hk" + System.nanoTime();
    return userService.create(new UserForm(
        username, "화면 확인", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private ManagedUser withRole(UserRole role) {
    ManagedUser managed = user();
    managed.changeRole(role, OffsetDateTime.now());
    return managed;
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser managed) {
    MoaUserDetails principal = new MoaUserDetails(managed);
    List<SimpleGrantedAuthority> authorities = principal.getAuthorities().stream()
        .map(granted -> new SimpleGrantedAuthority(granted.getAuthority())).toList();
    return new UsernamePasswordAuthenticationToken(principal, "", authorities);
  }
}
