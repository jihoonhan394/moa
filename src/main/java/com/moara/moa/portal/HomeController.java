package com.moara.moa.portal;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetService;
import com.moara.moa.connection.ConnectionSessionService;
import com.moara.moa.expiration.ExpirationRow;
import com.moara.moa.expiration.ExpirationService;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.permission.PermissionSetService;
import com.moara.moa.reservation.ReservationService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.solution.SolutionSequenceService;
import com.moara.moa.wiki.WikiPageService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 기관 관리자(TENANT_ADMIN) 대시보드. 자산 통계가 아니라 관리자가 실제로 궁금해하는
 * 계정·거버넌스·접속 활동을 보여준다(서버·솔루션 관리·현황은 인프라 관리자 몫).
 */
@Controller
public class HomeController {
  @org.springframework.beans.factory.annotation.Value("${moa.dashboard.recent-limit:6}")
  private int recentLimit;

  private final ManagedUserService userService;
  private final ConnectionSessionService sessionService;
  private final PermissionSetService permissionService;
  private final AssetService assetService;
  private final InventoryItemService inventoryService;
  private final ReservationService reservationService;
  private final WikiPageService wikiService;
  private final SolutionSequenceService sequenceService;
  private final ExpirationService expirationService;
  private final TenantContext tenantContext;

  public HomeController(
      ManagedUserService userService,
      ConnectionSessionService sessionService,
      PermissionSetService permissionService,
      AssetService assetService,
      InventoryItemService inventoryService,
      ReservationService reservationService,
      WikiPageService wikiService,
      SolutionSequenceService sequenceService,
      ExpirationService expirationService,
      TenantContext tenantContext) {
    this.userService = userService;
    this.sessionService = sessionService;
    this.permissionService = permissionService;
    this.assetService = assetService;
    this.inventoryService = inventoryService;
    this.reservationService = reservationService;
    this.wikiService = wikiService;
    this.sequenceService = sequenceService;
    this.expirationService = expirationService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/")
  public String home() {
    // 관리 역할이 없는 일반 사용자(신입)는 관리자 대시보드 대신 '내 워크스페이스'로 보낸다.
    var user = tenantContext.currentUser();
    boolean manager = user != null && (user.hasRole(UserRole.TENANT_ADMIN)
        || user.hasRole(UserRole.INFRA_MANAGER) || user.hasRole(UserRole.ASSET_MANAGER)
        || user.hasRole(UserRole.SYSTEM_ADMIN));
    return manager ? "redirect:/dashboard" : "redirect:/my/workspace";
  }

  @GetMapping("/dashboard")
  public String dashboard(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    List<ManagedUser> users = userService.findByTenant(tenantId);

    long activeUsers = users.stream()
        .filter(u -> u.hasRole(UserRole.USER) && u.getStatus() == UserStatus.ACTIVE).count();
    long infraManagers = users.stream().filter(u -> u.hasRole(UserRole.INFRA_MANAGER)).count();
    long pendingUsers = users.stream().filter(u -> u.getStatus() == UserStatus.PENDING).count();
    long disabledUsers = users.stream().filter(u -> u.getStatus() == UserStatus.DISABLED).count();

    // 만료 임박 접근권(7일 내): 사용자 개별 부착의 만료일 기준 집계.
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime soon = now.plusDays(7);
    long expiringGrants = permissionService.findAll(tenantId).stream()
        .flatMap(p -> permissionService.findUserAssignments(tenantId, p.getId()).stream())
        .filter(a -> a.getExpiresAt() != null
            && !a.getExpiresAt().isBefore(now) && !a.getExpiresAt().isAfter(soon))
        .count();

    // 최근 접속 이력(이름 해석).
    Map<UUID, String> userNames = users.stream()
        .collect(Collectors.toMap(ManagedUser::getId, ManagedUser::getUsername, (a, b) -> a));
    Map<UUID, String> assetNames = assetService.findAll(tenantId).stream()
        .collect(Collectors.toMap(Asset::getId, Asset::getName, (a, b) -> a));
    List<SessionRow> recentSessions = sessionService.findHistory(tenantId).stream()
        .limit(recentLimit)
        .map(s -> new SessionRow(
            s.getCreatedAt(),
            userNames.getOrDefault(s.getUserId(), "(알 수 없음)"),
            assetNames.getOrDefault(s.getAssetId(), "(삭제된 자산)"),
            s.getProtocol().name(),
            s.getStatus().name()))
        .toList();

    // 도메인 현황(오버뷰): 인벤토리·예약·위키·기동순서·만료.
    List<ExpirationRow> expirations = expirationService.findAll(tenantId);
    long expOverdue = expirations.stream().filter(r -> r.daysLeft() < 0).count();
    long expSoon = expirations.stream().filter(r -> r.daysLeft() >= 0 && r.daysLeft() <= 14).count();

    model.addAttribute("activeUsers", activeUsers);
    model.addAttribute("infraManagers", infraManagers);
    model.addAttribute("pendingUsers", pendingUsers);
    model.addAttribute("disabledUsers", disabledUsers);
    model.addAttribute("expiringGrants", expiringGrants);
    model.addAttribute("recentSessions", recentSessions);
    model.addAttribute("inventoryCount", inventoryService.findAll(tenantId).size());
    model.addAttribute("reservationUpcoming", reservationService.upcomingCount(tenantId));
    model.addAttribute("wikiCount", wikiService.findAll(tenantId).size());
    model.addAttribute("sequenceCount", sequenceService.findAll(tenantId).size());
    model.addAttribute("expirationsOverdue", expOverdue);
    model.addAttribute("expirationsSoon", expSoon);
    model.addAttribute("today", LocalDate.now());
    model.addAttribute("page", "dashboard");
    model.addAttribute("pageTitle", "대시보드");
    model.addAttribute("projectName", "MOA");
    return "index";
  }

  @GetMapping("/server-status")
  public String serverStatus(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    List<Asset> servers = assetService.findAllByType(tenantId, com.moara.moa.asset.AssetType.SERVER);
    long active = servers.stream().filter(s -> s.getStatus() == com.moara.moa.asset.AssetStatus.ACTIVE).count();
    model.addAttribute("servers", servers);
    model.addAttribute("serverTotal", servers.size());
    model.addAttribute("serverActive", active);
    model.addAttribute("serverInactive", servers.size() - active);
    model.addAttribute("page", "server-status");
    model.addAttribute("pageTitle", "서버 현황");
    model.addAttribute("projectName", "MOA");
    return "server-status";
  }

  public record SessionRow(
      OffsetDateTime at, String user, String asset, String protocol, String status) {}
}
