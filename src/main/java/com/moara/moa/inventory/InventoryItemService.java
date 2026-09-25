package com.moara.moa.inventory;

import com.moara.moa.notification.NotificationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실물·SW 인벤토리 관리(자산 관리자). 모든 조회·변경은 현재 기관으로 스코프된다. 등록·수정은 메타만,
 * 배정/회수/폐기는 별도 상태 전이로 다룬다. 퇴사 회수를 위해 사용자별 배정 조회·일괄 회수도 제공한다.
 */
@Service
@Transactional(readOnly = true)
public class InventoryItemService {
  private final InventoryItemRepository repository;

  private final InventoryCustodyService custodyService;
  private final InventoryPartService partService;
  private final NotificationService notificationService;

  public InventoryItemService(
      InventoryItemRepository repository, InventoryCustodyService custodyService,
      InventoryPartService partService, NotificationService notificationService) {
    this.repository = repository;
    this.custodyService = custodyService;
    this.partService = partService;
    this.notificationService = notificationService;
  }

  public List<InventoryItem> findAll(UUID tenantId) {
    return repository.findAllByTenantIdOrderByNameAsc(tenantId);
  }

  public InventoryItem findById(UUID tenantId, UUID id) {
    return repository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new InventoryItemNotFoundException(id));
  }

  @Transactional
  public InventoryItem create(UUID tenantId, InventoryItemForm form) {
    requireUniqueName(tenantId, form.name(), null);
    return repository.save(new InventoryItem(UUID.randomUUID(), tenantId, form, OffsetDateTime.now()));
  }

  @Transactional
  public InventoryItem update(UUID tenantId, UUID id, InventoryItemForm form) {
    InventoryItem item = findById(tenantId, id);
    requireUniqueName(tenantId, form.name(), id);
    item.applyMeta(form, OffsetDateTime.now());
    return repository.save(item);
  }

  @Transactional
  public void delete(UUID tenantId, UUID id) {
    repository.delete(findById(tenantId, id));
  }

  @Transactional
  public InventoryItem assign(UUID tenantId, UUID id, UUID userId) {
    return assign(tenantId, id, userId, null);
  }

  /**
   * 사용자에게 배정. 상태 변경과 함께 <b>보관 장부에 구간을 남긴다</b> — 그래야 "이 장비가
   * 지금까지 누구 손을 거쳤나"에 답할 수 있다. {@code assignedUserId}는 활성 구간의 캐시다.
   */
  @Transactional
  public InventoryItem assign(UUID tenantId, UUID id, UUID userId, UUID actorId) {
    InventoryItem item = findById(tenantId, id);
    item.assignTo(userId, OffsetDateTime.now());
    InventoryItem saved = repository.save(item);
    custodyService.toUser(tenantId, id, userId, "배정", actorId);
    // 받은 사람에게 알린다. 알림이 없으면 인수 확인을 누를 자리를 모른다 — 확인을
    // 요구하면서 알리지 않으면 영원히 "확인 대기"로 남는다.
    notificationService.notify(tenantId, userId,
        "자산을 받으셨습니다 — " + saved.getName(),
        "내 자산에서 내용을 확인하고 '받았습니다'를 눌러 주세요.",
        "/my/assets/" + id);
    return saved;
  }

  @Transactional
  public InventoryItem reclaim(UUID tenantId, UUID id) {
    return reclaim(tenantId, id, null, "회수");
  }

  @Transactional
  public InventoryItem reclaim(UUID tenantId, UUID id, UUID actorId, String reason) {
    InventoryItem item = findById(tenantId, id);
    item.reclaim(OffsetDateTime.now());
    InventoryItem saved = repository.save(item);
    custodyService.toWarehouse(tenantId, id, reason, actorId);
    return saved;
  }

  @Transactional
  public InventoryItem retire(UUID tenantId, UUID id) {
    return retire(tenantId, id, null);
  }

  /**
   * 폐기. 장착돼 있던 부품은 <b>같이 버리지 않고 떼어내 창고로</b> 돌린다 — 멀쩡한 RAM이
   * 장부에서 사라지면, 실물은 있는데 대장에만 없는 상태가 되어 실사에서 가장 곤란하다.
   */
  @Transactional
  public InventoryItem retire(UUID tenantId, UUID id, UUID actorId) {
    partService.detachAllFrom(tenantId, id, actorId);
    InventoryItem item = findById(tenantId, id);
    item.retire(OffsetDateTime.now());
    InventoryItem saved = repository.save(item);
    custodyService.toDisposed(tenantId, id, "폐기", actorId);
    return saved;
  }

  /** 특정 사용자에게 배정된 항목. */
  public List<InventoryItem> findAssignedTo(UUID tenantId, UUID userId) {
    return repository.findAllByTenantIdAndAssignedUserId(tenantId, userId);
  }

  /** 소유팀(그룹) 집합이 소유한 항목. 그룹이 없으면 빈 목록. */
  public List<InventoryItem> findOwnedByGroups(UUID tenantId, java.util.Collection<UUID> groupIds) {
    if (groupIds == null || groupIds.isEmpty()) {
      return List.of();
    }
    return repository.findAllByTenantIdAndOwnerGroupIdInOrderByNameAsc(tenantId, groupIds);
  }

  /** 소유팀(그룹) 배정/해제(자산 관리자). groupId=null이면 자산관리자 전용으로 되돌린다. */
  @Transactional
  public InventoryItem assignOwnerGroup(UUID tenantId, UUID id, UUID groupId) {
    InventoryItem item = findById(tenantId, id);
    item.assignOwnerGroup(groupId, OffsetDateTime.now());
    return repository.save(item);
  }

  /**
   * 퇴사 반납 요청: 배정된 항목을 <b>반납 대기</b>로 표시한다. 대상 건수 반환.
   *
   * <p>전에는 여기서 곧바로 {@code reclaim} + 창고 입고를 기록했다. 그러면 <b>아무도 물건을
   * 보지 않았는데 장부가 "창고에 있음"이라고 말한다.</b> 게다가 상태가 AVAILABLE이 되어 다음
   * 사람에게 배정까지 가능했고, 받으러 간 사람은 아무것도 찾지 못한다.
   *
   * <p>장부의 값어치는 확인된 사실에 있다. 확인하지 않은 기록은 없는 것보다 나쁘다 — 확인한
   * 구간과 구별되지 않은 채 확신을 가지고 틀리기 때문이다. 0.7.20의 인수 확인이 "관리자가
   * 일방적으로 쓴 장부는 한쪽 주장일 뿐"이라서 생겼는데 이 경로가 그것을 우회하고 있었다.
   * 하필 퇴사는 정산이 걸려 분쟁 가능성이 가장 높은 순간이다.
   *
   * <p><b>보관 구간은 건드리지 않는다.</b> 물건은 아직 그 사람에게 있고, 그것이 사실이다.
   * 자산 관리자가 실물을 확인하고 창고 입고를 기록할 때({@link #reclaim}) 구간이 닫힌다.
   *
   * <p>계정 차단은 이것과 무관하게 즉시 이뤄진다 — 물건을 기다리느라 접근을 열어 두면 안 된다.
   */
  @Transactional
  public long requestReturnFrom(UUID tenantId, UUID userId) {
    List<InventoryItem> assigned = repository.findAllByTenantIdAndAssignedUserId(tenantId, userId);
    OffsetDateTime now = OffsetDateTime.now();
    for (InventoryItem item : assigned) {
      item.awaitReturn(now);
    }
    repository.saveAll(assigned);
    return assigned.size();
  }

  /** 반납 대기(퇴사했지만 실물 미확인) 목록. */
  public List<InventoryItem> returnPending(UUID tenantId) {
    return repository.findAllByTenantIdAndStatus(tenantId, InventoryItemStatus.RETURN_PENDING);
  }

  /**
   * 이름 중복은 <b>상위 자산끼리만</b> 막는다. 부품은 같은 이름이 여러 장비에 들어가는 것이
   * 정상이고("RAM 32GB"), 부분 이동으로 행이 갈라질 때도 같은 이름이 생긴다.
   *
   * <p>V66에서 DB 제약을 걷어내고 판정이 여기로 왔다 — "부품일 때만 제외"에는 부분 유니크
   * 인덱스가 필요한데 H2가 지원하지 않기 때문이다(로컬·테스트가 H2다).
   */
  private void requireUniqueName(UUID tenantId, String name, UUID excludeId) {
    repository.findByTenantIdAndName(tenantId, name).stream()
        .filter(existing -> !existing.isPart())
        .filter(existing -> !existing.getId().equals(excludeId))
        .findFirst()
        .ifPresent(existing -> {
          throw new DuplicateInventoryItemException(name);
        });
  }
}
