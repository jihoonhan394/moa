package com.moara.moa.expiration;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetService;
import com.moara.moa.inventory.InventoryItem;
import com.moara.moa.inventory.InventoryCustody;
import com.moara.moa.inventory.InventoryCustodyService;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.inventory.InventoryItemStatus;
import com.moara.moa.permission.Permission;
import com.moara.moa.permission.PermissionSetService;
import com.moara.moa.permission.PermissionUserAssignment;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.tracker.ResourceTracker;
import com.moara.moa.tracker.ResourceTrackerService;
import com.moara.moa.tracker.TrackerTargetType;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여러 도메인의 "만료" 항목을 한곳에 모은다(비전 §2: 만료 놓치면 사고). 현재 소스: 인벤토리 라이선스 만료,
 * 기간제 접근 권한 만료, 기관 구독 만기. 임박(만료 지난 것 먼저)순으로 정렬해 돌려준다.
 */
@Service
@Transactional(readOnly = true)
public class ExpirationService {
  /**
   * 만료 판정 기준 시간대. JVM 기본 존에 맡기면 서버가 UTC일 때 KST 자정~09시 사이의
   * "오늘"이 하루 어긋나 D-day 계산과 알림 임계일 매칭이 빗나간다. 화면과 배치가 같은
   * 기준을 쓰도록 여기서 고정한다.
   */
  public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  private final InventoryItemService inventoryService;
  private final PermissionSetService permissionService;
  private final ManagedUserService userService;
  private final TenantService tenantService;
  private final ResourceTrackerService trackerService;
  private final AssetService assetService;
  private final InventoryCustodyService custodyService;

  public ExpirationService(
      InventoryItemService inventoryService, PermissionSetService permissionService,
      ManagedUserService userService, TenantService tenantService,
      ResourceTrackerService trackerService, AssetService assetService,
      InventoryCustodyService custodyService) {
    this.inventoryService = inventoryService;
    this.permissionService = permissionService;
    this.userService = userService;
    this.tenantService = tenantService;
    this.trackerService = trackerService;
    this.assetService = assetService;
    this.custodyService = custodyService;
  }

  public List<ExpirationRow> findAll(UUID tenantId) {
    LocalDate today = LocalDate.now(ZONE);
    List<ExpirationRow> rows = new ArrayList<>();

    // 1) 인벤토리 라이프사이클 만기(폐기 제외): 라이선스/기타(expiresAt) + 보증 + 리스·계약.
    for (InventoryItem item : inventoryService.findAll(tenantId)) {
      if (item.getStatus() == InventoryItemStatus.RETIRED) {
        continue;
      }
      String base = item.getType().getLabel()
          + (item.getCategory() != null ? " · " + item.getCategory() : "");
      if (item.getExpiresAt() != null) {
        rows.add(row("인벤토리", item.getName(), base, item.getExpiresAt(), today,
            ExpirationSourceType.INVENTORY, item.getId()));
      }
      if (item.getWarrantyEnds() != null) {
        rows.add(row("보증", item.getName(), base + " · 보증 만료", item.getWarrantyEnds(), today,
            ExpirationSourceType.INVENTORY, item.getId()));
      }
      if (item.getLeaseEnds() != null) {
        rows.add(row("리스/계약", item.getName(), base + " · 리스·계약 만료", item.getLeaseEnds(), today,
            ExpirationSourceType.INVENTORY, item.getId()));
      }
    }

    // 2) 기간제 접근 권한 만료.
    Map<UUID, String> userNames = userNames(tenantId);
    for (Permission permission : permissionService.findAll(tenantId)) {
      for (PermissionUserAssignment assignment : permissionService.findUserAssignments(tenantId, permission.getId())) {
        if (assignment.getExpiresAt() != null) {
          String who = userNames.getOrDefault(assignment.getUserId(), "(알 수 없음)");
          rows.add(row("접근 권한", permission.getName(), "→ " + who,
              assignment.getExpiresAt().toLocalDate(), today,
              ExpirationSourceType.ACCESS_GRANT, assignment.getUserId()));
        }
      }
    }

    // 3) 기관 구독 만기(무제한이면 제외).
    Tenant tenant = tenantService.getById(tenantId);
    if (tenant.getSubscriptionEnd() != null) {
      rows.add(row("구독", tenant.getName(), "기관 구독 만기", tenant.getSubscriptionEnd(), today,
          ExpirationSourceType.SUBSCRIPTION, tenantId));
    }

    // 4) 자원 만기·점검 항목(인벤토리/자산 공용, SSL 자동감지 포함).
    Map<UUID, String> inventoryNames = inventoryNames(tenantId);
    Map<UUID, String> assetNames = assetNames(tenantId);
    for (ResourceTracker tracker : trackerService.allForTenant(tenantId)) {
      boolean asset = tracker.getTargetType() == TrackerTargetType.ASSET;
      String targetName = asset
          ? assetNames.getOrDefault(tracker.getTargetId(), "(삭제된 자산)")
          : inventoryNames.getOrDefault(tracker.getTargetId(), "(삭제된 항목)");
      String category = tracker.isRecurring() ? "정기점검" : "만기/점검";
      String detail = tracker.getLabel()
          + (tracker.isRecurring() ? " · " + tracker.getRecurEveryDays() + "일 주기" : "")
          + (ResourceTrackerService.SOURCE_SSL.equals(tracker.getSource()) ? " · SSL 자동감지" : "");
      rows.add(row(category, targetName, detail, tracker.getDueOn(), today,
          asset ? ExpirationSourceType.ASSET : ExpirationSourceType.INVENTORY, tracker.getTargetId(),
          // 트래커는 편집 폼이 아니라 만기·점검 패널로 — 완료 처리·SSL 재탐지가 거기 있다.
          (asset ? "/assets/" : "/inventory/") + tracker.getTargetId() + "/trackers"));
    }

    // 5) 사외 반출(고객처 납품·업체 수리)의 반납 예정. 만료 대시보드에 얹으면 알림 배치와
    //    메일 발송이 그대로 따라온다 — 납품 나간 장비가 안 돌아오는 것을 잡는 게 목적이다.
    for (InventoryCustody custody : custodyService.open(tenantId)) {
      if (custody.getExpectedReturnOn() == null || custody.getHolderType().isInHouse()) {
        continue; // 사내 보관은 '반납'이라는 개념이 없고, 예정일이 없으면 초과도 없다
      }
      String itemName = inventoryNames.getOrDefault(custody.getItemId(), "(삭제된 항목)");
      String holder = custody.getHolderName() == null
          ? custody.getHolderType().getLabel() : custody.getHolderName();
      String detail = holder
          + (custody.getReason() == null ? "" : " · " + custody.getReason())
          + " · " + custody.getStartedOn() + " 반출";
      rows.add(row("반출 반납", itemName, detail, custody.getExpectedReturnOn(), today,
          ExpirationSourceType.INVENTORY, custody.getItemId(),
          "/inventory/" + custody.getItemId() + "/custody"));
    }

    rows.sort(Comparator.comparing(ExpirationRow::expiresOn));
    return rows;
  }

  private Map<UUID, String> inventoryNames(UUID tenantId) {
    Map<UUID, String> names = new HashMap<>();
    for (InventoryItem item : inventoryService.findAll(tenantId)) {
      names.put(item.getId(), item.getName());
    }
    return names;
  }

  private Map<UUID, String> assetNames(UUID tenantId) {
    Map<UUID, String> names = new HashMap<>();
    for (Asset asset : assetService.findAll(tenantId)) {
      names.put(asset.getId(), asset.getName());
    }
    return names;
  }

  private ExpirationRow row(
      String category, String label, String detail, LocalDate expiresOn, LocalDate today,
      ExpirationSourceType sourceType, UUID sourceId) {
    return row(category, label, detail, expiresOn, today, sourceType, sourceId,
        defaultLink(sourceType, sourceId));
  }

  private ExpirationRow row(
      String category, String label, String detail, LocalDate expiresOn, LocalDate today,
      ExpirationSourceType sourceType, UUID sourceId, String link) {
    long daysLeft = ChronoUnit.DAYS.between(today, expiresOn);
    return new ExpirationRow(
        category, label, detail, expiresOn, daysLeft, sourceType, sourceId, link);
  }

  /**
   * 항목을 손볼 수 있는 기본 화면. 인벤토리·자산은 편집 폼으로 보낸다 — 만료일·보증·리스 날짜를
   * 고치는 곳이 거기이고, 만기·점검 패널로 가는 링크도 그 화면에 있다. 구독 만기는 기관 설정
   * 소관(플랫폼 콘솔)이라 여기서 열지 않는다.
   */
  private String defaultLink(ExpirationSourceType sourceType, UUID sourceId) {
    if (sourceType == null || sourceId == null) {
      return null;
    }
    return switch (sourceType) {
      case INVENTORY -> "/inventory/" + sourceId + "/edit";
      case ASSET -> "/assets/" + sourceId + "/edit";
      case ACCESS_GRANT -> "/permissions";
      case SUBSCRIPTION -> null;
    };
  }

  private Map<UUID, String> userNames(UUID tenantId) {
    Map<UUID, String> names = new HashMap<>();
    for (ManagedUser u : userService.findByTenant(tenantId)) {
      names.put(u.getId(), u.getName());
    }
    return names;
  }
}
