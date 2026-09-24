package com.moara.moa.inventory;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자산 보관 장부. 물건이 손을 옮길 때마다 <b>이전 구간을 닫고 새 구간을 연다</b>.
 *
 * <p>이 서비스는 {@link InventoryItemService}를 알지 못한다(반대 방향으로만 의존). 품목의
 * 존재·소유권 검증은 부르는 쪽이 이미 하고 있으므로, 여기서는 구간을 잇는 일만 한다.
 *
 * <h2>왜 삭제가 없나</h2>
 * 구간은 닫기만 하고 지우지 않는다. 장부의 값어치는 지난 기록에 있고, 잘못 넣은 이동을 지우면
 * "이 장비가 어디를 거쳐 왔나"에 답할 수 없게 된다. 잘못된 이동은 <b>되돌리는 이동</b>으로
 * 바로잡는다 — 회계 장부와 같은 규칙이다.
 */
@Service
@Transactional(readOnly = true)
public class InventoryCustodyService {
  private final InventoryCustodyRepository repository;

  public InventoryCustodyService(InventoryCustodyRepository repository) {
    this.repository = repository;
  }

  /** 품목의 보관 이력(최근 구간이 위). */
  public List<InventoryCustody> history(UUID tenantId, UUID itemId) {
    return repository.findByTenantIdAndItemIdOrderByStartedOnDescCreatedAtDesc(tenantId, itemId);
  }

  /** 현재 보관 구간. 한 번도 이동을 기록하지 않은 기존 품목은 비어 있다. */
  public Optional<InventoryCustody> current(UUID tenantId, UUID itemId) {
    return repository.findFirstByTenantIdAndItemIdAndEndedOnIsNull(tenantId, itemId);
  }

  /** 기관에서 지금 열려 있는 구간 전체(현황·반납 초과 집계용). */
  public List<InventoryCustody> open(UUID tenantId) {
    return repository.findByTenantIdAndEndedOnIsNull(tenantId);
  }

  /** 반납 예정일이 지났는데 아직 안 돌아온 것. 납품 나간 장비를 잃어버리지 않기 위한 조회다. */
  public List<InventoryCustody> returnOverdue(UUID tenantId, LocalDate today) {
    return open(tenantId).stream().filter(c -> c.isReturnOverdue(today)).toList();
  }

  /**
   * 보관을 옮긴다. 열려 있던 구간을 오늘로 닫고 새 구간을 연다.
   *
   * <p>같은 보관자로 다시 옮기는 호출은 <b>무시한다</b> — 화면에서 같은 버튼을 두 번 눌렀을 때
   * 의미 없는 구간이 쌓이면 이력을 읽기 어려워진다. 다만 반납 예정일·메모가 바뀌는 경우는
   * 사실이 달라진 것이므로 새 구간을 연다.
   *
   * @param actorId 기록한 사람. 배치(퇴사 회수 등)는 알 수 없어 null이다 — "누가 했나"는
   *     감사 로그가 답하고 이 장부는 "어디에 있었나"를 답한다.
   */
  @Transactional
  public InventoryCustody transfer(
      UUID tenantId, UUID itemId, InventoryHolderType holderType, UUID holderId,
      String holderName, LocalDate expectedReturnOn, String reason, String note, UUID actorId) {
    LocalDate today = LocalDate.now();
    Optional<InventoryCustody> active = current(tenantId, itemId);
    if (active.isPresent() && isSameHolder(active.get(), holderType, holderId, holderName)
        && java.util.Objects.equals(active.get().getExpectedReturnOn(), expectedReturnOn)) {
      return active.get();
    }
    int quantity = active.map(InventoryCustody::getQuantity).orElse(1);
    active.ifPresent(custody -> {
      custody.close(today);
      repository.save(custody);
    });
    return repository.save(new InventoryCustody(
        UUID.randomUUID(), tenantId, itemId, quantity, holderType, holderId, holderName,
        today, expectedReturnOn, reason, note, actorId, OffsetDateTime.now()));
  }

  /** 사용자에게 배정. */
  @Transactional
  public void toUser(UUID tenantId, UUID itemId, UUID userId, String reason, UUID actorId) {
    transfer(tenantId, itemId, InventoryHolderType.USER, userId, null, null, reason, null, actorId);
  }

  /** 창고로 회수. */
  @Transactional
  public void toWarehouse(UUID tenantId, UUID itemId, String reason, UUID actorId) {
    transfer(tenantId, itemId, InventoryHolderType.WAREHOUSE, null, null, null, reason, null, actorId);
  }

  /** 폐기. 구간을 닫지 않고 DISPOSED 구간을 여는 이유는, 폐기도 하나의 보관 상태이기 때문이다. */
  @Transactional
  public void toDisposed(UUID tenantId, UUID itemId, String reason, UUID actorId) {
    transfer(tenantId, itemId, InventoryHolderType.DISPOSED, null, null, null, reason, null, actorId);
  }

  private boolean isSameHolder(
      InventoryCustody custody, InventoryHolderType type, UUID holderId, String holderName) {
    if (custody.getHolderType() != type) {
      return false;
    }
    if (type.isInternalTarget()) {
      return java.util.Objects.equals(custody.getHolderId(), holderId);
    }
    String existing = custody.getHolderName() == null ? "" : custody.getHolderName();
    String incoming = holderName == null ? "" : holderName.trim();
    return existing.equals(incoming);
  }
}
