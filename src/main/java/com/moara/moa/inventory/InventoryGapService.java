package com.moara.moa.inventory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자산 대장 점검: 대장에서 <b>빠진 것</b>과 <b>앞뒤가 안 맞는 것</b>을 제품이 스스로 보여 준다.
 *
 * <p>필드를 늘리기 전에 지금 대장이 얼마나 비어 있는지부터 보이게 하는 것이 목적이다
 * ({@code doc/실무자료-기능제안-20260925/05-코드대조-검토.md} §9-C). 막지 않고 보여 주기만 한다 —
 * 날짜 역전은 중고 장비처럼 정당한 경우가 있어 검증으로 막으면 현장이 우회로를 찾는다
 * ({@code doc/논의-2026-10-02.md} §2-2).
 *
 * <p>폐기 자산은 보지 않는다. 장착 구성품은 보증·책임자·보관을 상위 장비가 대신하므로
 * 날짜 점검만 한다.
 */
@Service
public class InventoryGapService {
  /** "오래 기록 없음"의 기준. 이보다 오래 아무 보관 기록이 없으면 실사 우선 대상이다. */
  static final int STALE_DAYS = 365;

  public record Gap(String key, String title, String hint, List<Row> rows) {}

  /** 걸린 자산과 왜 걸렸는지(마지막 기록일, 어긋난 날짜 등). 이유가 이름 자체면 빈 문자열. */
  public record Row(InventoryItem item, String detail) {}

  private final InventoryItemRepository itemRepository;
  private final InventoryCustodyRepository custodyRepository;

  public InventoryGapService(
      InventoryItemRepository itemRepository, InventoryCustodyRepository custodyRepository) {
    this.itemRepository = itemRepository;
    this.custodyRepository = custodyRepository;
  }

  @Transactional(readOnly = true)
  public List<Gap> gaps(UUID tenantId, LocalDate today) {
    List<InventoryItem> live = itemRepository.findAllByTenantIdOrderByNameAsc(tenantId).stream()
        .filter(item -> item.getStatus() != InventoryItemStatus.RETIRED)
        .toList();
    Map<UUID, LocalDate> lastRecord = lastRecordDates(tenantId);
    Map<UUID, InventoryHolderType> openHolder = new HashMap<>();
    custodyRepository.findByTenantIdAndEndedOnIsNull(tenantId)
        .forEach(open -> openHolder.put(open.getItemId(), open.getHolderType()));
    LocalDate staleBefore = today.minusDays(STALE_DAYS);

    Predicate<InventoryItem> standalonePhysical = item ->
        item.getType() == InventoryItemType.PHYSICAL && item.getParentItemId() == null;

    List<Gap> gaps = new ArrayList<>();
    gaps.add(gap("no-warranty", "보증 만료일 없음",
        "실물 자산인데 보증 만료일이 비어 있어 만료 관리에 잡히지 않습니다.",
        live, standalonePhysical.and(item -> item.getWarrantyEnds() == null), item -> ""));
    gaps.add(gap("no-owner", "책임자 없음",
        "창고 밖에 있는데 사용자도 담당 부서도 정해지지 않아, 잃어버려도 물어볼 사람이 없습니다.",
        // 창고 재고는 뺀다: 담당 부서를 두지 않는 기관에서는 재고 전부가 걸려 목록이 의미를 잃는다.
        // 위치는 상태가 아니라 보관 장부로 본다 — 고객처·업체 반출은 상태를 "가용"으로 둔 채 나간다.
        live, item -> item.getParentItemId() == null
            && item.getAssignedUserId() == null && item.getOwnerGroupId() == null
            && !inWarehouseOrDepartment(item, openHolder.get(item.getId())),
        item -> item.getStatus().getLabel()));
    gaps.add(gap("stale", "1년 넘게 보관 기록 없음",
        "배정·회수·이동 기록이 1년 넘게 없습니다. 실사는 여기부터 하면 잃어버렸을 가능성이 높은 것부터 확인합니다.",
        live, standalonePhysical.and(item -> lastRecord(item, lastRecord).isBefore(staleBefore)),
        item -> lastRecord.containsKey(item.getId())
            ? "마지막 기록 " + lastRecord.get(item.getId())
            : "기록 없음 · " + item.getCreatedAt().toLocalDate() + " 등록"));
    gaps.add(gap("date-mismatch", "날짜가 앞뒤가 안 맞음",
        "취득일이 미래이거나, 보증·리스·만료일이 취득일보다 앞섭니다. 중고 장비처럼 맞는 경우도 있어 막지는 않습니다.",
        live, item -> dateMismatch(item, today), InventoryGapService::dateDetail));
    return gaps;
  }

  /** 창고에 있거나(기록이 없으면 등록 직후 가용 = 창고) 부서가 보관 중이면 책임 소재가 있다. */
  private static boolean inWarehouseOrDepartment(InventoryItem item, InventoryHolderType holder) {
    if (holder == null) {
      return item.getStatus() == InventoryItemStatus.AVAILABLE;
    }
    return holder == InventoryHolderType.WAREHOUSE || holder == InventoryHolderType.GROUP;
  }

  static boolean dateMismatch(InventoryItem item, LocalDate today) {
    LocalDate bought = item.getPurchaseDate();
    if (bought == null) {
      return false;
    }
    return bought.isAfter(today)
        || before(item.getWarrantyEnds(), bought)
        || before(item.getLeaseEnds(), bought)
        || before(item.getExpiresAt(), bought);
  }

  private static String dateDetail(InventoryItem item) {
    StringBuilder detail = new StringBuilder("취득 " + item.getPurchaseDate());
    if (item.getWarrantyEnds() != null) {
      detail.append(" · 보증 ").append(item.getWarrantyEnds());
    }
    if (item.getLeaseEnds() != null) {
      detail.append(" · 리스 ").append(item.getLeaseEnds());
    }
    if (item.getExpiresAt() != null) {
      detail.append(" · 만료 ").append(item.getExpiresAt());
    }
    return detail.toString();
  }

  private static LocalDate lastRecord(InventoryItem item, Map<UUID, LocalDate> lastRecord) {
    return lastRecord.getOrDefault(item.getId(), item.getCreatedAt().toLocalDate());
  }

  private static boolean before(LocalDate date, LocalDate bought) {
    return date != null && date.isBefore(bought);
  }

  private Map<UUID, LocalDate> lastRecordDates(UUID tenantId) {
    Map<UUID, LocalDate> last = new HashMap<>();
    for (Object[] row : custodyRepository.findLastRecordDates(tenantId)) {
      LocalDate started = (LocalDate) row[1];
      LocalDate ended = (LocalDate) row[2];
      last.put((UUID) row[0], ended != null && ended.isAfter(started) ? ended : started);
    }
    return last;
  }

  private static Gap gap(String key, String title, String hint, List<InventoryItem> items,
      Predicate<InventoryItem> test, Function<InventoryItem, String> detail) {
    return new Gap(key, title, hint,
        items.stream().filter(test).map(item -> new Row(item, detail.apply(item))).toList());
  }
}
