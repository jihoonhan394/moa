package com.moara.moa.audit;

import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUser;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.moara.moa.user.ManagedUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

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

  @GetMapping("/audit")
  public String audit(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    Map<UUID, String> actorNames = userService.findByTenant(tenantId).stream()
        .collect(Collectors.toMap(ManagedUser::getId, ManagedUser::getName));

    List<AuditView> logs = auditLogService.findByTenant(tenantId).stream()
        .map(log -> toView(log, actorNames))
        .toList();

    model.addAttribute("logs", logs);
    // 어제 요약. 로그는 쌓이기만 하고 아무도 읽지 않는 것이 실제 문제라, 화면 맨 위에 둔다.
    model.addAttribute("digest", digestService.yesterday(tenantId));
    model.addAttribute("page", "audit");
    model.addAttribute("pageTitle", "감사 로그");
    model.addAttribute("projectName", "MOA");
    return "audit/list";
  }

  private AuditView toView(AuditLog log, Map<UUID, String> actorNames) {
    String actor = actorNames.getOrDefault(log.getActorUserId(), log.getActorUserId().toString());
    String target = log.getTargetType() == null ? "-"
        : log.getTargetType() + (log.getTargetId() != null ? " (" + log.getTargetId() + ")" : "");
    return new AuditView(
        log.getCreatedAt(), actor, log.getAction(), target, log.getResult(), log.getMessage());
  }

  public record AuditView(
      OffsetDateTime createdAt, String actor, String action, String target,
      AuditResult result, String message) {}
}
