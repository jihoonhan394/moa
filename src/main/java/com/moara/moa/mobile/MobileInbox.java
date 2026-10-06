package com.moara.moa.mobile;

import com.moara.moa.access.AccessApprovalService;
import com.moara.moa.access.AccessRequest;
import com.moara.moa.consumable.ConsumableRequest;
import com.moara.moa.consumable.ConsumableRequestService;
import com.moara.moa.inventory.InventoryCustody;
import com.moara.moa.inventory.InventoryCustodyService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * "내가 처리해야 하는 것"을 정하는 한 곳.
 *
 * <h2>왜 따로 뽑았나</h2>
 *
 * 처음에는 승인 화면({@link MobileWorkController})과 홈 뱃지({@link MobileAssetController})가
 * 각자 셌다. 그 사이 승인 화면에만 <b>역할·본인 필터</b>가 붙으면서 둘이 어긋났고,
 * 실제로 홈은 <b>"승인 45건 대기"</b>, 화면은 <b>0건</b>을 보여 줬다. 뱃지가 거짓말을 하면
 * 사람들은 뱃지를 안 보게 되고, 그러면 뱃지가 있을 이유가 없다.
 *
 * <p>세 가지 출처가 모두 <b>기관 전체</b>를 주는 메서드라는 점이 함정이다 —
 * {@code findOpen}, {@code awaitingConfirmation} 은 필터 없이 쓰면 남의 일감이 섞인다.
 * 그 필터를 여기 한 번만 둔다.
 */
@Component
public class MobileInbox {

  private final AccessApprovalService approvalService;
  private final ConsumableRequestService consumableRequestService;
  private final InventoryCustodyService custodyService;
  private final TenantContext tenantContext;

  public MobileInbox(
      AccessApprovalService approvalService, ConsumableRequestService consumableRequestService,
      InventoryCustodyService custodyService, TenantContext tenantContext) {
    this.approvalService = approvalService;
    this.consumableRequestService = consumableRequestService;
    this.custodyService = custodyService;
    this.tenantContext = tenantContext;
  }

  /** 내 일감 세 묶음. 화면은 이걸 그리고, 홈 뱃지는 {@link Pending#total()} 만 쓴다. */
  public record Pending(
      List<AccessRequest> access,
      List<ConsumableRequest> supplies,
      List<InventoryCustody> receipts) {

    public int total() {
      return access.size() + supplies.size() + receipts.size();
    }
  }

  public Pending pendingFor(UUID tenantId, UUID me) {
    return new Pending(
        approvalService.pendingForApprover(tenantId, me),
        // ⚠️ findOpen 은 기관 전체의 요청을 준다. 소모품 담당이 아닌 사람에게 보이면 남의
        // 요청이 그대로 노출되고 누를 수도 없다.
        canHandleSupplies() ? consumableRequestService.findOpen(tenantId) : List.of(),
        // ⚠️ awaitingConfirmation 도 기관 전체다. 인수 확인은 **받은 본인만** 할 수 있으므로
        // (InventoryCustodyService.confirmReceipt) 내 것만 남긴다.
        custodyService.awaitingConfirmation(tenantId).stream()
            .filter(custody -> me.equals(custody.getHolderId()))
            .toList());
  }

  /** 소모품 요청을 접수·거절할 수 있는 역할인가. 화면 노출과 처리 권한이 같은 기준을 쓴다. */
  public boolean canHandleSupplies() {
    var user = tenantContext.currentUser();
    return user.hasRole(UserRole.ASSET_MANAGER) || user.hasRole(UserRole.TENANT_ADMIN);
  }
}
