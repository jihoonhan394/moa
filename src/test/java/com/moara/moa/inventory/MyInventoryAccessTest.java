package com.moara.moa.inventory;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 일반 사용자가 보는 내 자산 화면. 관리 화면({@code /inventory/**})은 자산 관리자 전용이라
 * 당사자가 자기 자산의 이력을 볼 수 없었다 — 그 구멍을 {@code /my/assets/{id}}가 메운다.
 *
 * <p>이 경로는 {@code /my/**}라 <b>인증만 있으면 열린다</b>. 그래서 "무엇을 볼 수 있는지"는
 * URL 규칙이 아니라 컨트롤러가 판정한다. 그 판정이 이 테스트의 전부다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MyInventoryAccessTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;
  @Autowired private InventoryItemService inventoryService;

  @Test
  void 배정받은_사람은_자기_자산의_이력을_본다() throws Exception {
    Tenant tenant = tenant();
    ManagedUser owner = user(tenant.getId());
    InventoryItem item = item(tenant.getId(), "내노트북");
    inventoryService.assign(tenant.getId(), item.getId(), owner.getId(), owner.getId());

    mockMvc.perform(get("/my/assets/" + item.getId()).with(authentication(auth(owner))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("보관 이력")))
        .andExpect(content().string(containsString("내게 배정된")));
  }

  /**
   * 남의 자산은 <b>존재 자체를 알리지 않는다</b>(404). 403으로 돌려주면 "그런 자산이 있긴
   * 하다"를 알려 주는 셈이라, ID를 넣어 보며 남의 대장을 훑을 수 있게 된다.
   */
  @Test
  void 남의_자산은_없는_것처럼_보인다() throws Exception {
    Tenant tenant = tenant();
    ManagedUser owner = user(tenant.getId());
    ManagedUser stranger = user(tenant.getId());
    InventoryItem item = item(tenant.getId(), "남의노트북");
    inventoryService.assign(tenant.getId(), item.getId(), owner.getId(), owner.getId());

    mockMvc.perform(get("/my/assets/" + item.getId()).with(authentication(auth(stranger))))
        .andExpect(status().isNotFound());
  }

  /** 아무에게도 배정되지 않은 창고 자산도 마찬가지다. */
  @Test
  void 배정되지_않은_자산은_열리지_않는다() throws Exception {
    Tenant tenant = tenant();
    ManagedUser someone = user(tenant.getId());
    InventoryItem item = item(tenant.getId(), "창고노트북");

    mockMvc.perform(get("/my/assets/" + item.getId()).with(authentication(auth(someone))))
        .andExpect(status().isNotFound());
  }

  /** 다른 기관 사용자에게는 당연히 보이지 않는다(기관 스코프가 먼저 막는다). */
  @Test
  void 다른_기관_사용자에게는_보이지_않는다() throws Exception {
    Tenant a = tenant();
    Tenant b = tenant();
    ManagedUser ownerA = user(a.getId());
    ManagedUser userB = user(b.getId());
    InventoryItem item = item(a.getId(), "A기관노트북");
    inventoryService.assign(a.getId(), item.getId(), ownerA.getId(), ownerA.getId());

    mockMvc.perform(get("/my/assets/" + item.getId()).with(authentication(auth(userB))))
        .andExpect(status().isNotFound());
  }

  private Tenant tenant() {
    String suffix = String.valueOf(System.nanoTime());
    return tenantService.createTenant(new CreateTenantCommand("내자산기관" + suffix, "MY" + suffix));
  }

  private ManagedUser user(UUID tenantId) {
    String username = "mine" + System.nanoTime();
    return userService.create(tenantId, new UserForm(
        username, "사용자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE),
        Set.of(UserRole.USER));
  }

  private InventoryItem item(UUID tenantId, String name) {
    return inventoryService.create(tenantId, new InventoryItemForm(
        name + System.nanoTime(), InventoryItemType.PHYSICAL, "노트북",
        "SN-" + System.nanoTime(), null, null));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    var auths = user.getRoles().stream()
        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
        .collect(Collectors.toSet());
    return new UsernamePasswordAuthenticationToken(principal, "n/a", auths);
  }
}
