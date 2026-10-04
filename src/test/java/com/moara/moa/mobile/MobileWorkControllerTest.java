package com.moara.moa.mobile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.consumable.ConsumableItemForm;
import com.moara.moa.consumable.ConsumableRequestService;
import com.moara.moa.consumable.ConsumableService;
import com.moara.moa.inventory.InventoryItem;
import com.moara.moa.inventory.InventoryItemForm;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.inventory.InventoryItemType;
import com.moara.moa.notification.NotificationService;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileWorkControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ManagedUserService userService;
  @Autowired private NotificationService notificationService;
  @Autowired private ConsumableService consumableService;
  @Autowired private ConsumableRequestService consumableRequestService;
  @Autowired private InventoryItemService inventoryService;

  @Test
  @DisplayName("승인 화면이 뜬다")
  void 승인_화면() throws Exception {
    ManagedUser user = user();
    mockMvc.perform(get("/m/approvals").with(auth(user)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("⚠️ 소모품 요청은 담당이 아닌 사람에게 보이지 않는다 — 남의 요청이 노출되면 안 된다")
  void 소모품_요청은_담당에게만() throws Exception {
    ManagedUser requester = user();
    ManagedUser outsider = user();
    ManagedUser manager = user(UserRole.ASSET_MANAGER);
    UUID tenantId = requester.getTenantId();

    var item = consumableService.create(tenantId,
        new ConsumableItemForm("요청시험품-" + System.nanoTime(), "사무용품", "개", 30, null));
    consumableRequestService.request(tenantId, item.getId(), requester.getId(), "급히 필요합니다");

    // 일반 사용자에게는 안 보인다
    mockMvc.perform(get("/m/approvals").with(auth(outsider)))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.not(Matchers.containsString(item.getName()))));

    // 자산 관리자에게는 보인다
    mockMvc.perform(get("/m/approvals").with(auth(manager)))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString(item.getName())));
  }

  @Test
  @DisplayName("⚠️ 권한 없이 소모품 요청을 접수할 수 없다 — 서비스에 검사가 없어 여기서 막는다")
  void 권한_없이_접수할_수_없다() throws Exception {
    ManagedUser requester = user();
    ManagedUser outsider = user();
    UUID tenantId = requester.getTenantId();

    var item = consumableService.create(tenantId,
        new ConsumableItemForm("접수시험품-" + System.nanoTime(), "사무용품", "개", 30, null));
    consumableRequestService.request(tenantId, item.getId(), requester.getId(), "필요");
    UUID requestId = consumableRequestService.findOpen(tenantId).stream()
        .filter(r -> r.getItemId().equals(item.getId()))
        .findFirst().orElseThrow().getId();

    mockMvc.perform(post("/m/approvals/supply/" + requestId + "/ack")
            .with(auth(outsider)).with(csrf()))
        .andExpect(status().isForbidden());

    // 그리고 실제로 상태가 바뀌지 않았다
    assertThat(consumableRequestService.findOpen(tenantId))
        .anyMatch(r -> r.getId().equals(requestId));
  }

  @Test
  @DisplayName("⚠️ 인수 확인에는 내 것만 나온다 — 남이 뭘 받았는지 보이면 안 된다")
  void 인수_확인은_내_것만() throws Exception {
    ManagedUser holder = user();
    ManagedUser other = user();
    UUID tenantId = holder.getTenantId();

    InventoryItem item = inventoryService.create(tenantId, new InventoryItemForm(
        "인수시험-" + System.nanoTime(), InventoryItemType.PHYSICAL, null, null, null, null));
    inventoryService.assign(tenantId, item.getId(), holder.getId(), holder.getId());

    mockMvc.perform(get("/m/approvals").with(auth(holder)))
        .andExpect(content().string(Matchers.containsString(item.getName())));

    mockMvc.perform(get("/m/approvals").with(auth(other)))
        .andExpect(content().string(Matchers.not(Matchers.containsString(item.getName()))));
  }

  @Test
  @DisplayName("알림이 뜨고 모두 읽음이 동작한다")
  void 알림() throws Exception {
    ManagedUser user = user();
    notificationService.notify(user.getTenantId(), user.getId(),
        "시험 알림-" + System.nanoTime(), "본문", "/m");

    assertThat(notificationService.unreadCount(user.getTenantId(), user.getId())).isPositive();

    mockMvc.perform(get("/m/notifications").with(auth(user)))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("시험 알림")));

    mockMvc.perform(post("/m/notifications/read-all").with(auth(user)).with(csrf()))
        .andExpect(status().is3xxRedirection());

    assertThat(notificationService.unreadCount(user.getTenantId(), user.getId())).isZero();
  }

  @Test
  @DisplayName("⚠️ 솔루션 연락처는 인프라 관리자만 본다")
  void 솔루션은_인프라_관리자만() throws Exception {
    ManagedUser plain = user();
    ManagedUser infra = user(UserRole.INFRA_MANAGER);

    mockMvc.perform(get("/m/solutions").with(auth(plain)))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/m/solutions").with(auth(infra)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("내 요청·공지·내 것 화면이 뜬다")
  void 내_것_화면들() throws Exception {
    ManagedUser user = user();
    mockMvc.perform(get("/m/me").with(auth(user))).andExpect(status().isOk());
    mockMvc.perform(get("/m/me/requests").with(auth(user))).andExpect(status().isOk());
    mockMvc.perform(get("/m/notices").with(auth(user))).andExpect(status().isOk());
  }

  @Test
  @DisplayName("홈이 숫자를 보여 준다")
  void 홈_숫자() throws Exception {
    ManagedUser user = user();
    notificationService.notify(user.getTenantId(), user.getId(), "홈시험", "본문", null);

    mockMvc.perform(get("/m").with(auth(user)))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("안 읽음")));
  }

  /**
   * ⚠️ 권한을 손으로 넣지 않는다.
   *
   * <p>운영에서는 {@code MoaUserDetails.getAuthorities()} 가 <b>엔티티의 역할</b>에서 파생된다.
   * 테스트에서만 authority 를 따로 주면 URL 보안(authority)과 서비스 검사(엔티티 역할)가
   * 어긋나, 실제로는 없는 문제를 만들거나 있는 문제를 가린다.
   */
  private org.springframework.test.web.servlet.request.RequestPostProcessor auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return authentication(
        new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
  }

  private ManagedUser user() {
    return user(UserRole.USER);
  }

  private ManagedUser user(UserRole... roles) {
    String username = "work" + System.nanoTime();
    ManagedUser created = userService.create(new UserForm(
        username, "일꾼" + System.nanoTime(), username + "@example.com",
        "safe-password-123", UserStatus.ACTIVE));
    userService.assignRoles(created.getTenantId(), created.getId(), Set.of(roles));
    return userService.findById(created.getId());
  }
}
