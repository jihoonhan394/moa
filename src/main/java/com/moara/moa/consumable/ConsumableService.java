package com.moara.moa.consumable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소모품 품목과 주문 이력. 모든 조회·변경은 현재 기관으로 스코프된다.
 *
 * <p>재고 수량을 다루지 않는다(4a 범위). 알림 문구가 <i>"현재 재고를 확인해주세요"</i>인 것
 * 자체가 시스템이 재고를 몰라도 된다는 뜻이고, 입출고를 전부 기록하게 하면 대부분 안 해서
 * 오히려 숫자를 못 믿게 된다.
 */
@Service
@Transactional(readOnly = true)
public class ConsumableService {
  private final ConsumableItemRepository itemRepository;
  private final ConsumableOrderRepository orderRepository;

  public ConsumableService(
      ConsumableItemRepository itemRepository, ConsumableOrderRepository orderRepository) {
    this.itemRepository = itemRepository;
    this.orderRepository = orderRepository;
  }

  public List<ConsumableItem> findAll(UUID tenantId) {
    return itemRepository.findByTenantIdOrderByNameAsc(tenantId);
  }

  /** 요청 화면이 쓰는 목록 — 쓰지 않는 품목은 고를 수 없어야 한다. */
  public List<ConsumableItem> findActive(UUID tenantId) {
    return itemRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId);
  }

  public ConsumableItem findById(UUID tenantId, UUID id) {
    return itemRepository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new ConsumableItemNotFoundException(id));
  }

  @Transactional
  public ConsumableItem create(UUID tenantId, ConsumableItemForm form) {
    requireUniqueName(tenantId, form.name(), null);
    return itemRepository.save(
        new ConsumableItem(UUID.randomUUID(), tenantId, form, OffsetDateTime.now()));
  }

  @Transactional
  public ConsumableItem update(UUID tenantId, UUID id, ConsumableItemForm form) {
    ConsumableItem item = findById(tenantId, id);
    requireUniqueName(tenantId, form.name(), id);
    item.applyMeta(form, OffsetDateTime.now());
    return itemRepository.save(item);
  }

  @Transactional
  public void assignOwnerGroup(UUID tenantId, UUID id, UUID groupId) {
    ConsumableItem item = findById(tenantId, id);
    item.assignOwnerGroup(groupId, OffsetDateTime.now());
    itemRepository.save(item);
  }

  /** 쓰지 않는 품목은 지우지 않고 끈다 — 지우면 주문 이력까지 사라져 과거를 볼 수 없다. */
  @Transactional
  public void setActive(UUID tenantId, UUID id, boolean active) {
    ConsumableItem item = findById(tenantId, id);
    OffsetDateTime now = OffsetDateTime.now();
    if (active) {
      item.activate(now);
    } else {
      item.deactivate(now);
    }
    itemRepository.save(item);
  }

  /** 실측 주기를 설정값으로 받아들인다(담당자가 승인했을 때만 부른다). */
  @Transactional
  public void adoptCycle(UUID tenantId, UUID id, Integer cycleDays) {
    ConsumableItem item = findById(tenantId, id);
    item.changeCycleDays(cycleDays, OffsetDateTime.now());
    itemRepository.save(item);
  }

  public List<ConsumableOrder> orders(UUID tenantId, UUID itemId) {
    return orderRepository.findByTenantIdAndItemIdOrderByOrderedOnDesc(tenantId, itemId);
  }

  /** 주문 기록. 미래 날짜는 받지 않는다 — 아직 일어나지 않은 일로 소비율을 계산할 수 없다. */
  @Transactional
  public ConsumableOrder addOrder(
      UUID tenantId, UUID itemId, ConsumableOrderForm form, UUID actorId) {
    findById(tenantId, itemId); // 소유권 검증
    if (form.orderedOn().isAfter(LocalDate.now())) {
      throw new IllegalArgumentException("주문일은 오늘보다 뒤일 수 없습니다.");
    }
    return orderRepository.save(new ConsumableOrder(
        UUID.randomUUID(), tenantId, itemId, form.orderedOn(), form.quantityOrOne(),
        form.note(), actorId, OffsetDateTime.now()));
  }

  /**
   * 기관 전체 품목의 예측을 한 번에. 품목마다 주문을 따로 조회하면 N+1이 되므로 한 번 읽어
   * 메모리에서 나눈다(소모품 품목은 수십 개 규모다).
   */
  public Map<UUID, ConsumableForecast> forecasts(UUID tenantId, LocalDate today) {
    Map<UUID, List<ConsumableOrder>> byItem = orderRepository.findByTenantId(tenantId).stream()
        .collect(Collectors.groupingBy(ConsumableOrder::getItemId));
    Map<UUID, ConsumableForecast> out = new HashMap<>();
    for (ConsumableItem item : findAll(tenantId)) {
      out.put(item.getId(), ConsumableForecast.of(
          byItem.getOrDefault(item.getId(), List.of()), item.getCycleDays(), today));
    }
    return out;
  }

  public ConsumableForecast forecast(UUID tenantId, UUID itemId, LocalDate today) {
    ConsumableItem item = findById(tenantId, itemId);
    return ConsumableForecast.of(orders(tenantId, itemId), item.getCycleDays(), today);
  }

  private void requireUniqueName(UUID tenantId, String name, UUID excludeId) {
    itemRepository.findByTenantIdAndName(tenantId, name.trim()).ifPresent(existing -> {
      if (!existing.getId().equals(excludeId)) {
        throw new DuplicateConsumableItemException(name);
      }
    });
  }
}
