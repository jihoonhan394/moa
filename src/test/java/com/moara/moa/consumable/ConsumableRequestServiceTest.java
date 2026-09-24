package com.moara.moa.consumable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moara.moa.notification.NotificationService;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

/**
 * 소모품 요청. 직원이 "떨어졌어요"를 남기고 담당자가 처리하는 흐름.
 *
 * <p>가장 값어치 있는 연결은 <b>주문 등록이 요청을 완료시키는 것</b>이다 — 담당자는 한 번
 * 입력하는데 요청 처리와 주문 이력이 동시에 남고, 그 이력이 주기 예측을 먹여 살린다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConsumableRequestServiceTest {
  @Autowired private ConsumableRequestService requestService;
  @Autowired private ConsumableService consumableService;
  @Autowired private NotificationService notificationService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void 요청하면_목록에_쌓인다() {
    UUID tenantId = tenant();
    ManagedUser requester = user(tenantId);
    ConsumableItem item = item(tenantId);

    boolean first = requestService.request(tenantId, item.getId(), requester.getId(), "제로 콜라요");

    assertThat(first).isTrue();
    assertThat(requestService.findMine(tenantId, requester.getId())).hasSize(1);
    assertThat(requestService.findOpen(tenantId)).hasSize(1);
  }

  /** 같은 품목의 두 번째 요청은 담당자에게 다시 알리지 않는다 — 다섯 번 오면 알림을 꺼 버린다. */
  @Test
  void 같은_품목의_두_번째_요청은_다시_알리지_않는다() {
    UUID tenantId = tenant();
    ConsumableItem item = item(tenantId);

    assertThat(requestService.request(tenantId, item.getId(), user(tenantId).getId(), null)).isTrue();
    assertThat(requestService.request(tenantId, item.getId(), user(tenantId).getId(), null)).isFalse();
    assertThat(requestService.openCountByItem(tenantId).get(item.getId())).isEqualTo(2);
  }

  /**
   * 주문을 등록하면 그 품목의 미처리 요청이 한꺼번에 완료된다. 담당자가 따로 누를 것이 없고,
   * 주문 이력은 저절로 쌓인다.
   */
  @Test
  void 주문을_등록하면_미처리_요청이_한꺼번에_완료된다() {
    UUID tenantId = tenant();
    ManagedUser a = user(tenantId);
    ManagedUser b = user(tenantId);
    ManagedUser manager = user(tenantId);
    ConsumableItem item = item(tenantId);
    requestService.request(tenantId, item.getId(), a.getId(), null);
    requestService.request(tenantId, item.getId(), b.getId(), null);

    ConsumableOrder order = consumableService.addOrder(tenantId, item.getId(),
        new ConsumableOrderForm(LocalDate.now(), 3, null), manager.getId());
    int closed = requestService.fulfillByOrder(tenantId, item.getId(), order.getId(), manager.getId());

    assertThat(closed).isEqualTo(2);
    assertThat(requestService.findOpen(tenantId)).isEmpty();
    // 요청자들이 결과를 안다.
    assertThat(notificationService.list(tenantId, a.getId()))
        .anyMatch(n -> n.getTitle().contains("주문했습니다"));
  }

  /** 거절·보류는 사유가 필수다 — 이유 없이 닫히면 요청자는 같은 요청을 반복한다. */
  @Test
  void 사유_없이는_거절도_보류도_안_된다() {
    UUID tenantId = tenant();
    ManagedUser requester = user(tenantId);
    ManagedUser manager = user(tenantId);
    ConsumableItem item = item(tenantId);
    requestService.request(tenantId, item.getId(), requester.getId(), null);
    UUID requestId = requestService.findOpen(tenantId).get(0).getId();

    assertThatThrownBy(() -> requestService.reject(tenantId, requestId, manager.getId(), "  "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> requestService.hold(tenantId, requestId, manager.getId(), null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** 거절하면 요청자가 사유를 알림으로 받는다. */
  @Test
  void 거절하면_요청자에게_사유가_전달된다() {
    UUID tenantId = tenant();
    ManagedUser requester = user(tenantId);
    ManagedUser manager = user(tenantId);
    ConsumableItem item = item(tenantId);
    requestService.request(tenantId, item.getId(), requester.getId(), null);
    UUID requestId = requestService.findOpen(tenantId).get(0).getId();

    requestService.reject(tenantId, requestId, manager.getId(), "재고가 아직 충분합니다");

    assertThat(notificationService.list(tenantId, requester.getId()))
        .anyMatch(n -> n.getBody().contains("재고가 아직 충분합니다"));
    assertThat(requestService.findOpen(tenantId)).isEmpty();
  }

  /** 보류는 재검토일을 함께 남길 수 있다 — 없으면 보류가 영원히 목록에 남는다. */
  @Test
  void 보류는_재검토일을_남길_수_있다() {
    UUID tenantId = tenant();
    ManagedUser requester = user(tenantId);
    ManagedUser manager = user(tenantId);
    ConsumableItem item = item(tenantId);
    requestService.request(tenantId, item.getId(), requester.getId(), null);
    UUID requestId = requestService.findOpen(tenantId).get(0).getId();
    LocalDate review = LocalDate.now().plusDays(14);

    requestService.hold(tenantId, requestId, manager.getId(), "다음 분기 예산으로", review);

    ConsumableRequest held = requestService.findById(tenantId, requestId);
    assertThat(held.getStatus()).isEqualTo(ConsumableRequestStatus.HELD);
    assertThat(held.getReviewOn()).isEqualTo(review);
    assertThat(requestService.findOpen(tenantId)).hasSize(1); // 보류는 아직 열린 상태다
  }

  /** 요청자는 아무도 손대기 전에만 취소할 수 있다. */
  @Test
  void 처리가_시작되면_요청자도_취소할_수_없다() {
    UUID tenantId = tenant();
    ManagedUser requester = user(tenantId);
    ManagedUser manager = user(tenantId);
    ConsumableItem item = item(tenantId);
    requestService.request(tenantId, item.getId(), requester.getId(), null);
    UUID requestId = requestService.findOpen(tenantId).get(0).getId();
    requestService.acknowledge(tenantId, requestId, manager.getId());

    assertThatThrownBy(() -> requestService.cancel(tenantId, requestId, requester.getId()))
        .isInstanceOf(AccessDeniedException.class);
  }

  /** 남의 요청은 취소할 수 없다. */
  @Test
  void 남의_요청은_취소할_수_없다() {
    UUID tenantId = tenant();
    ManagedUser requester = user(tenantId);
    ManagedUser stranger = user(tenantId);
    ConsumableItem item = item(tenantId);
    requestService.request(tenantId, item.getId(), requester.getId(), null);
    UUID requestId = requestService.findOpen(tenantId).get(0).getId();

    assertThatThrownBy(() -> requestService.cancel(tenantId, requestId, stranger.getId()))
        .isInstanceOf(AccessDeniedException.class);
  }

  /** 사용 중지한 품목은 요청할 수 없다 — 고를 수 있으면 안 쓰는 물건 요청이 쌓인다. */
  @Test
  void 사용_중지한_품목은_요청할_수_없다() {
    UUID tenantId = tenant();
    ConsumableItem item = item(tenantId);
    consumableService.setActive(tenantId, item.getId(), false);

    assertThatThrownBy(() ->
        requestService.request(tenantId, item.getId(), user(tenantId).getId(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** 기관 경계 — 다른 기관의 품목에는 요청할 수 없다. */
  @Test
  void 다른_기관_품목에는_요청할_수_없다() {
    UUID a = tenant();
    UUID b = tenant();
    ConsumableItem item = item(a);

    assertThatThrownBy(() -> requestService.request(b, item.getId(), user(b).getId(), null))
        .isInstanceOf(ConsumableItemNotFoundException.class);
    assertThat(requestService.findAll(b)).isEmpty();
  }

  private UUID tenant() {
    String suffix = String.valueOf(System.nanoTime());
    return tenantService.createTenant(new CreateTenantCommand("요청기관" + suffix, "CR" + suffix)).getId();
  }

  private ManagedUser user(UUID tenantId) {
    String username = "req" + System.nanoTime();
    return userService.create(tenantId, new UserForm(
        username, "직원", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private ConsumableItem item(UUID tenantId) {
    return consumableService.create(tenantId, new ConsumableItemForm(
        "음료수" + System.nanoTime(), "간식", "박스", null, null));
  }
}
