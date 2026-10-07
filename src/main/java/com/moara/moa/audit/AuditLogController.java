package com.moara.moa.audit;

import com.moara.moa.security.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.moara.moa.user.ManagedUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 감사 로그 조회 화면. 현재 테넌트의 관리자 행위(그룹/권한/사용자 변경, 웹 자산 열람 등)를 최신순으로 보여준다.
 * append-only 감사 축이므로 조회 전용이다.
 */
@Controller
public class AuditLogController {
  private final AuditLogService auditLogService;
  private final ManagedUserService userService;
  private final AuditDigestService digestService;
  private final TenantContext tenantContext;

  public AuditLogController(
      AuditLogService auditLogService, ManagedUserService userService, TenantContext tenantContext, AuditDigestService digestService) {
    this.auditLogService = auditLogService;
    this.userService = userService;
    this.digestService = digestService;
    this.tenantContext = tenantContext;
  }

  /** 기간 선택지(일). 0 은 전 기간. */
  private static final int[] PERIODS = {7, 30, 90, 0};

  /**
   * 감사 로그. <b>기본 최근 30일, 페이지당 50건.</b>
   *
   * <p>전에는 기관의 전체 로그를 읽어 한 페이지에 그렸다. 감사 로그는 특권 행위마다 한 줄
   * 쌓이고 지우지 않으므로, 1년 뒤에는 그 화면이 열리지 않는다. 기간 기본값을 둔 이유는
   * 따로 있다 — <b>감사 로그를 여는 이유는 거의 항상 "요즘 무슨 일이 있었나"</b>이고,
   * 2년 전 기록은 그때 찾아 들어가는 것이다.
   */
  @GetMapping("/audit")
  public String audit(
      @RequestParam(defaultValue = "30") int days,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    Map<UUID, String> actorNames = userService.namesByTenant(tenantId);

    OffsetDateTime since = days > 0 ? OffsetDateTime.now().minusDays(days) : null;
    Page<AuditLog> found = auditLogService.findByTenant(tenantId, since, page);
    List<AuditView> logs = found.getContent().stream()
        .map(log -> toView(log, actorNames))
        .toList();

    model.addAttribute("logs", logs);
    model.addAttribute("days", days);
    model.addAttribute("periods", PERIODS);
    model.addAttribute("totalCount", found.getTotalElements());
    // 페이저는 화면마다 복사하지 않는다 — fragments/pager 하나를 네 목록이 함께 쓴다.
    model.addAttribute("pager", com.moara.moa.web.support.PageView.of(found, "/audit",
        com.moara.moa.web.support.PageView.conditions("days", days)));
    // 어제 요약. 로그는 쌓이기만 하고 아무도 읽지 않는 것이 실제 문제라, 화면 맨 위에 둔다.
    model.addAttribute("digest", digestService.yesterday(tenantId));
    model.addAttribute("page", "audit");
    model.addAttribute("pageTitle", "감사 로그");
    model.addAttribute("projectName", "MOA");
    return "audit/list";
  }

  private AuditView toView(AuditLog log, Map<UUID, String> actorNames) {
    String actor = actorLabel(log.getActorUserId(), actorNames);
    String target = log.getTargetType() == null ? "-"
        : log.getTargetType() + (log.getTargetId() != null ? " (" + log.getTargetId() + ")" : "");
    return new AuditView(
        log.getCreatedAt(), actor, log.getAction(), target, log.getResult(), log.getMessage());
  }

  /**
   * 행위자가 <b>없을 수 있다</b> — 없는 계정으로의 로그인 실패가 그렇고(V72), 그것이 가장
   * 보고 싶은 신호다. 전에는 여기서 바로 {@code toString()}을 불러, 그런 기록이 처음
   * 들어오는 순간 감사 화면 전체가 NPE로 죽었을 것이다.
   */
  private static String actorLabel(UUID actorId, Map<UUID, String> actorNames) {
    if (actorId == null) {
      return "(등록되지 않은 계정)";
    }
    return actorNames.getOrDefault(actorId, actorId.toString());
  }

  public record AuditView(
      OffsetDateTime createdAt, String actor, String action, String target,
      AuditResult result, String message) {}
}
