package com.moara.moa.inventory;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자산 구성품(부품). RAM·SSD·OS 라이선스처럼 <b>장비 안에 들어가는 자산</b>을 다룬다.
 *
 * <p>부품을 위한 별도 테이블은 없다. 부품도 그냥 자산이고, 다른 점은 "어느 장비에 들어가
 * 있나"({@code parentItemId}) 하나뿐이다. 그 이동은 보관 장부에 PARENT_ITEM 구간으로 남으므로
 * "이 RAM이 어느 장비를 거쳐 왔나"도 그대로 조회된다.
 *
 * <h2>부분 이동 — 왜 행을 쪼개나</h2>
 * "RAM 32GB 2개 중 1개를 다른 PC로" 는 한 행의 수량을 줄이고 새 행을 만드는 일이다.
 * 합치지 않는 이유는 각 행이 <b>자기 이력을 온전히</b> 갖게 하기 위해서다. 화면에서 같은
 * 품목끼리 합산해 보여 주면 사용자는 차이를 느끼지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class InventoryPartService {
  /** 장비 → 부품 → 하위부품. 실무에서 그 이상은 거의 없고, 깊어질수록 이력이 읽기 어려워진다. */
  private static final int MAX_DEPTH = 3;

  private final InventoryItemRepository repository;
  private final InventoryCustodyService custodyService;

  public InventoryPartService(
      InventoryItemRepository repository, InventoryCustodyService custodyService) {
    this.repository = repository;
    this.custodyService = custodyService;
  }

  /** 장비에 장착된 구성품. */
  public List<InventoryItem> partsOf(UUID tenantId, UUID parentId) {
    return repository.findAllByTenantIdAndParentItemId(tenantId, parentId);
  }

  /** 부품으로 장착할 수 있는 후보: 같은 기관의 미장착 자산 중 자기 자신과 자손을 뺀 것. */
  public List<InventoryItem> attachableTo(UUID tenantId, UUID parentId) {
    Set<UUID> forbidden = descendantsAndSelf(tenantId, parentId);
    return repository.findAllByTenantIdAndParentItemIdIsNull(tenantId).stream()
        .filter(item -> !forbidden.contains(item.getId()))
        .filter(item -> item.getStatus() != InventoryItemStatus.RETIRED)
        .toList();
  }

  /**
   * 부품을 장비에 장착한다. 수량을 지정하면 그만큼만 떼어 옮긴다(부분 이동).
   *
   * @param quantity 옮길 개수. 부품의 전체 수량과 같으면 행을 통째로 옮기고, 적으면 쪼갠다.
   */
  @Transactional
  public InventoryItem attach(UUID tenantId, UUID partId, UUID parentId, int quantity, UUID actorId) {
    InventoryItem part = require(tenantId, partId);
    InventoryItem parent = require(tenantId, parentId);
    requireNoCycle(tenantId, partId, parentId);
    requireDepth(tenantId, parentId);
    if (parent.getStatus() == InventoryItemStatus.RETIRED) {
      throw new IllegalArgumentException("폐기된 장비에는 부품을 장착할 수 없습니다.");
    }
    int moving = normalizeQuantity(part, quantity);
    InventoryItem target = moving < part.getQuantity() ? split(part, moving) : part;
    OffsetDateTime now = OffsetDateTime.now();
    target.attachTo(parentId, now);
    repository.save(target);
    custodyService.transfer(tenantId, target.getId(), InventoryHolderType.PARENT_ITEM, parentId,
        null, null, "부품 장착", null, actorId);
    return target;
  }

  /** 부품을 떼어내 창고로 되돌린다. */
  @Transactional
  public void detach(UUID tenantId, UUID partId, UUID actorId) {
    InventoryItem part = require(tenantId, partId);
    if (!part.isPart()) {
      throw new IllegalArgumentException("장착된 부품이 아닙니다.");
    }
    part.attachTo(null, OffsetDateTime.now());
    repository.save(part);
    custodyService.toWarehouse(tenantId, partId, "부품 분리", actorId);
  }

  /**
   * 부모가 폐기될 때 부품을 떼어 창고로 돌린다. 같이 폐기하면 멀쩡한 RAM이 장부에서 사라진다
   * — 실물은 남아 있는데 대장에만 없는 상태가 실사에서 가장 곤란하다.
   */
  @Transactional
  public int detachAllFrom(UUID tenantId, UUID parentId, UUID actorId) {
    List<InventoryItem> parts = partsOf(tenantId, parentId);
    for (InventoryItem part : parts) {
      detach(tenantId, part.getId(), actorId);
    }
    return parts.size();
  }

  /**
   * 부분 이동: 원래 행의 수량을 줄이고 옮길 만큼을 새 행으로 뗀다. 새 행은 이름·분류·사양을
   * 그대로 물려받되 시리얼은 비운다 — 같은 시리얼이 두 행에 있으면 어느 쪽이 진짜인지 알 수 없다.
   */
  private InventoryItem split(InventoryItem source, int moving) {
    OffsetDateTime now = OffsetDateTime.now();
    source.changeQuantity(source.getQuantity() - moving, now);
    repository.save(source);
    InventoryItem moved = new InventoryItem(
        UUID.randomUUID(), source.getTenantId(),
        new InventoryItemForm(source.getName(), source.getType(), source.getCategory(), null,
            source.getExpiresAt(), source.getPurchaseDate(), source.getWarrantyEnds(),
            source.getLeaseEnds(), source.getNote()),
        now);
    moved.changeQuantity(moving, now);
    return repository.save(moved);
  }

  /**
   * 시리얼이 있으면 쪼갤 수 없다 — 시리얼은 개체 하나를 가리키므로 "2개 중 1개"라는 말이
   * 성립하지 않는다. 그 경우 항상 행 전체가 움직인다.
   */
  private int normalizeQuantity(InventoryItem part, int requested) {
    if (part.getSerialNo() != null && !part.getSerialNo().isBlank()) {
      return part.getQuantity();
    }
    if (requested <= 0 || requested >= part.getQuantity()) {
      return part.getQuantity();
    }
    return requested;
  }

  /** A의 부모를 A의 자손으로 지정하면 트리가 고리가 되어 조회가 무한히 돈다. */
  private void requireNoCycle(UUID tenantId, UUID partId, UUID parentId) {
    if (partId.equals(parentId) || descendantsAndSelf(tenantId, partId).contains(parentId)) {
      throw new IllegalArgumentException("자기 자신이나 자기 부품 안에는 장착할 수 없습니다.");
    }
  }

  private void requireDepth(UUID tenantId, UUID parentId) {
    int depth = 1;
    UUID cursor = parentId;
    Set<UUID> seen = new HashSet<>();
    while (cursor != null && seen.add(cursor)) {
      InventoryItem item = repository.findByTenantIdAndId(tenantId, cursor).orElse(null);
      if (item == null) {
        break;
      }
      cursor = item.getParentItemId();
      depth++;
      if (depth > MAX_DEPTH) {
        throw new IllegalArgumentException("부품 계층은 " + MAX_DEPTH + "단계까지만 가능합니다.");
      }
    }
  }

  private Set<UUID> descendantsAndSelf(UUID tenantId, UUID rootId) {
    Set<UUID> found = new HashSet<>();
    List<UUID> queue = new ArrayList<>();
    queue.add(rootId);
    while (!queue.isEmpty()) {
      UUID current = queue.remove(0);
      if (!found.add(current)) {
        continue;
      }
      partsOf(tenantId, current).forEach(child -> queue.add(child.getId()));
    }
    return found;
  }

  /** 기관 경계 검증. 부모와 자식이 다른 기관이면 그 자체로 교차 기관 노출이다. */
  private InventoryItem require(UUID tenantId, UUID id) {
    return repository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new InventoryItemNotFoundException(id));
  }
}
