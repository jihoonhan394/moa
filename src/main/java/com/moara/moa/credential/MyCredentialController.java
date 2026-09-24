package com.moara.moa.credential;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.security.TenantContext;
import com.moara.moa.wiki.WikiSpace;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * '내 크리덴셜' — 공유받은 자격증명을 확인한다(비IT 사용자의 공유기/프린터 id·pw). 열람은 공유가 있을 때만
 * 가능하고 감사 기록된다. 평문은 열람 요청 시 1회 표시하며 로그/감사에 남기지 않는다.
 */
@Controller
public class MyCredentialController {
  private final CredentialShareService shareService;
  private final com.moara.moa.wiki.WikiSpaceService wikiSpaceService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public MyCredentialController(
      CredentialShareService shareService, com.moara.moa.wiki.WikiSpaceService wikiSpaceService,
      AuditLogService auditLogService, TenantContext tenantContext) {
    this.shareService = shareService;
    this.wikiSpaceService = wikiSpaceService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/my/credentials")
  public String list(Model model) {
    populate(model);
    return "my/credentials";
  }

  @PostMapping("/my/credentials/{id}/reveal")
  public String reveal(@PathVariable UUID id, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID userId = tenantContext.currentUserId();
    try {
      CredentialService.ResolvedCredential revealed = shareService.reveal(tenantId, id, userId);
      model.addAttribute("revealedId", id);
      model.addAttribute("revealedUsername", revealed.username());
      model.addAttribute("revealedSecret", revealed.secret());
      // 감사: 열람 사실만 기록(평문 미기록).
      auditLogService.recordTenantAction(
          tenantId, userId, "CREDENTIAL_REVEAL", "Credential", id, AuditResult.SUCCESS, null);
    } catch (RuntimeException exception) {
      model.addAttribute("revealError", "열람 권한이 없습니다.");
    }
    populate(model);
    return "my/credentials";
  }

  private void populate(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    model.addAttribute("credentials", shareService.sharedCredentials(tenantId, tenantContext.currentUserId()));
    Map<UUID, String> spaceNames = new HashMap<>();
    for (WikiSpace s : wikiSpaceService.findAll(tenantId)) {
      spaceNames.put(s.getId(), s.getName());
    }
    model.addAttribute("spaceNames", spaceNames);
    model.addAttribute("page", "my-credentials");
  }
}
