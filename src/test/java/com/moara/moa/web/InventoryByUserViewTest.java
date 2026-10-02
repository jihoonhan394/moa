package com.moara.moa.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.inventory.InventoryItem;
import com.moara.moa.inventory.InventoryItemForm;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.inventory.InventoryItemType;
import com.moara.moa.inventory.InventoryOffboardHandler;
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
 * 사람 기준 보유 자산 화면 — <b>퇴사 반납 체크리스트</b>.
 *
 * <p>0.9.4에서 퇴사 처리가 "창고 입고"를 적지 않게 바꿨다(아무도 물건을 보지 않았으므로).
 * 그래서 반납 대기가 생기는데, 그것을 사람 기준으로 모아 볼 화면이 없었다 — 자산 목록에서
 * 이름으로 훑어야 했다. 만들어 놓은 기능이 쓰이지 않는 상태가 가장 아깝다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryByUserViewTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ManagedUserService userService;
  @Autowired private InventoryItemService inventoryService;
  @Autowired private InventoryOffboardHandler offboardHandler;

  /** 배정된 자산이 그 사람 이름 아래 모여 보인다. */
  @Test
  void showsWhatOnePersonHolds() throws Exception {
    ManagedUser holder = user();
    InventoryItem item = item("보유노트북");
    inventoryService.assign(Tenant.DEFAULT_TENANT_ID, item.getId(), holder.getId(), null);

    mockMvc.perform(get("/inventory/by-user/" + holder.getId())
            .with(authentication(auth(withRole(UserRole.ASSET_MANAGER)))))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString(item.getName())))
        .andExpect(content().string(Matchers.containsString(holder.getName())));
  }

  /**
   * 퇴사한 사람의 화면에 <b>반납 대기 안내</b>가 떠야 한다. 이 화면의 존재 이유다 —
   * 실물을 확인하기 전에는 창고에 있다고 적지 않으므로, 누군가는 받으러 가야 한다.
   */
  @Test
  void warnsAboutUnconfirmedReturnAfterOffboard() throws Exception {
    ManagedUser leaver = user();
    InventoryItem item = item("퇴사노트북");
    inventoryService.assign(Tenant.DEFAULT_TENANT_ID, item.getId(), leaver.getId(), null);
    offboardHandler.offboard(Tenant.DEFAULT_TENANT_ID, leaver.getId());

    mockMvc.perform(get("/inventory/by-user/" + leaver.getId())
            .with(authentication(auth(withRole(UserRole.ASSET_MANAGER)))))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("반납 대기 1건")))
        .andExpect(content().string(Matchers.containsString("실물을 아직 확인하지 않았습니다")));
  }

  /** 자산이 없는 사람도 빈 화면이 아니라 설명이 보인다. */
  @Test
  void explainsWhenNothingIsHeld() throws Exception {
    ManagedUser nobody = user();

    mockMvc.perform(get("/inventory/by-user/" + nobody.getId())
            .with(authentication(auth(withRole(UserRole.ASSET_MANAGER)))))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("배정된 자산이 없습니다")));
  }

  /**
   * 자산 데이터는 자산 권한으로 본다. 경로를 자산 쪽에 둔 이유가 이것이고,
   * {@code SecurityConfig}의 {@code /inventory/**} 규칙이 그대로 적용된다.
   */
  @Test
  void onlyAssetManagerCanOpenIt() throws Exception {
    ManagedUser holder = user();

    for (ManagedUser viewer : List.of(user(), withRole(UserRole.TENANT_ADMIN),
        withRole(UserRole.INFRA_MANAGER))) {
      mockMvc.perform(get("/inventory/by-user/" + holder.getId())
              .with(authentication(auth(viewer))))
          .andExpect(status().isForbidden());
    }
  }

  /**
   * 없는 사용자는 404다. 권한 오류(403)가 아니라 <b>없음</b>으로 답하는 이유는, 403이
   * "그런 사용자는 있다"를 알려 주는 셈이라 id를 넣어 보며 남의 기관을 훑을 수 있게 되기
   * 때문이다(AGENTS.md의 기관 격리 방침).
   */
  @Test
  void unknownUserIsNotFound() throws Exception {
    mockMvc.perform(get("/inventory/by-user/" + java.util.UUID.randomUUID())
            .with(authentication(auth(withRole(UserRole.ASSET_MANAGER)))))
        .andExpect(status().isNotFound());
  }

  // ── 도우미 ────────────────────────────────────────────────────────────────

  private ManagedUser user() {
    String username = "hold" + System.nanoTime();
    return userService.create(new UserForm(
        username, "보유자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private ManagedUser withRole(UserRole role) {
    ManagedUser managed = user();
    managed.changeRole(role, OffsetDateTime.now());
    return managed;
  }

  private InventoryItem item(String name) {
    return inventoryService.create(Tenant.DEFAULT_TENANT_ID, new InventoryItemForm(
        name + System.nanoTime(), InventoryItemType.PHYSICAL, "노트북",
        "SN-" + System.nanoTime(), null, null));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser managed) {
    MoaUserDetails principal = new MoaUserDetails(managed);
    List<SimpleGrantedAuthority> authorities = principal.getAuthorities().stream()
        .map(granted -> new SimpleGrantedAuthority(granted.getAuthority())).toList();
    return new UsernamePasswordAuthenticationToken(principal, "", authorities);
  }
}
