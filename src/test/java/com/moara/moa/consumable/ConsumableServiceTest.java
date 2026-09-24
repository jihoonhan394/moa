package com.moara.moa.consumable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.TenantService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 소모품 품목·주문 이력. 예측 규칙 자체는 {@link ConsumableForecastTest}가 보고,
 * 여기서는 저장·경계·기관 격리를 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConsumableServiceTest {
  @Autowired private ConsumableService service;
  @Autowired private TenantService tenantService;

  @Test
  void 품목을_등록하고_주문을_쌓으면_예측이_나온다() {
    UUID tenantId = tenant();
    ConsumableItem item = service.create(tenantId, form("A4용지", null));

    service.addOrder(tenantId, item.getId(), order("2026-01-01", 1), null);
    service.addOrder(tenantId, item.getId(), order("2026-01-31", 1), null);
    service.addOrder(tenantId, item.getId(), order("2026-02-20", 1), null);
    service.addOrder(tenantId, item.getId(), order("2026-03-17", 1), null);

    var forecast = service.forecast(tenantId, item.getId(), LocalDate.of(2026, 3, 20));
    assertThat(forecast.basis()).isEqualTo(ConsumableForecast.Basis.MEASURED);
    assertThat(forecast.cycleDays()).isEqualTo(25);
    assertThat(service.orders(tenantId, item.getId())).hasSize(4);
  }

  /** 미래 날짜는 받지 않는다 — 아직 일어나지 않은 일로 소비율을 계산할 수 없다. */
  @Test
  void 미래_날짜_주문은_거부한다() {
    UUID tenantId = tenant();
    ConsumableItem item = service.create(tenantId, form("미래용지", null));

    assertThatThrownBy(() -> service.addOrder(tenantId, item.getId(),
        new ConsumableOrderForm(LocalDate.now().plusDays(1), 1, null), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("오늘보다 뒤");
  }

  @Test
  void 같은_이름은_두_번_등록되지_않는다() {
    UUID tenantId = tenant();
    // 이름을 고정해야 중복이 성립한다 — form()은 기관 간 충돌을 피하려고 접미사를 붙인다.
    ConsumableItemForm fixed =
        new ConsumableItemForm("중복용지", "사무용품", "박스", null, null);
    service.create(tenantId, fixed);

    assertThatThrownBy(() -> service.create(tenantId, fixed))
        .isInstanceOf(DuplicateConsumableItemException.class);
  }

  /** 쓰지 않는 품목은 지우지 않고 끈다 — 지우면 "작년에 얼마나 샀나"에 답할 수 없다. */
  @Test
  void 사용_중지해도_주문_이력은_남는다() {
    UUID tenantId = tenant();
    ConsumableItem item = service.create(tenantId, form("중지용지", null));
    service.addOrder(tenantId, item.getId(), order("2026-01-01", 2), null);

    service.setActive(tenantId, item.getId(), false);

    assertThat(service.findById(tenantId, item.getId()).isActive()).isFalse();
    assertThat(service.orders(tenantId, item.getId())).hasSize(1);
    assertThat(service.findActive(tenantId)).extracting(ConsumableItem::getId)
        .doesNotContain(item.getId());
    assertThat(service.findAll(tenantId)).extracting(ConsumableItem::getId)
        .contains(item.getId());
  }

  /** 예상 주기는 담당자가 승인해야 바뀐다(몰래 고치지 않는다). */
  @Test
  void 예상_주기를_실측값으로_바꿀_수_있다() {
    UUID tenantId = tenant();
    ConsumableItem item = service.create(tenantId, form("보정용지", 30));

    service.adoptCycle(tenantId, item.getId(), 18);

    assertThat(service.findById(tenantId, item.getId()).getCycleDays()).isEqualTo(18);
  }

  @Test
  void 다른_기관의_소모품은_보이지_않는다() {
    UUID a = tenant();
    UUID b = tenant();
    ConsumableItem item = service.create(a, form("A기관용지", null));

    assertThat(service.findAll(b)).isEmpty();
    assertThatThrownBy(() -> service.findById(b, item.getId()))
        .isInstanceOf(ConsumableItemNotFoundException.class);
    assertThatThrownBy(() -> service.addOrder(b, item.getId(), order("2026-01-01", 1), null))
        .isInstanceOf(ConsumableItemNotFoundException.class);
  }

  /** 기관 전체 예측을 한 번에 — 품목마다 조회하면 N+1이 된다. */
  @Test
  void 기관_전체_예측을_한_번에_계산한다() {
    UUID tenantId = tenant();
    ConsumableItem a = service.create(tenantId, form("일괄용지A", 14));
    ConsumableItem b = service.create(tenantId, form("일괄용지B", null));

    var all = service.forecasts(tenantId, LocalDate.now());

    assertThat(all).containsKeys(a.getId(), b.getId());
    assertThat(all.get(a.getId()).basis()).isEqualTo(ConsumableForecast.Basis.CONFIGURED);
    assertThat(all.get(b.getId()).basis()).isEqualTo(ConsumableForecast.Basis.NONE);
  }

  private UUID tenant() {
    String suffix = String.valueOf(System.nanoTime());
    return tenantService.createTenant(new CreateTenantCommand("소모품기관" + suffix, "CS" + suffix)).getId();
  }

  private ConsumableItemForm form(String name, Integer cycleDays) {
    return new ConsumableItemForm(name + System.nanoTime(), "사무용품", "박스", cycleDays, null);
  }

  private ConsumableOrderForm order(String date, int quantity) {
    return new ConsumableOrderForm(LocalDate.parse(date), quantity, null);
  }
}
