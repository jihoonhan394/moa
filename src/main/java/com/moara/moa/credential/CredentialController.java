package com.moara.moa.credential;

import com.moara.moa.audit.TenantAuditRecorder;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.wiki.WikiSpace;
import com.moara.moa.wiki.WikiSpaceService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 자격증명(볼트) 관리 UI. 비밀은 저장 시 암호화되고 <b>평문은 다시 보여주지 않는다</b>(쓰기전용).
 * 관리자 전용(SecurityConfig). 모든 조회/변경은 현재 테넌트로 스코프.
 */
@Controller
public class CredentialController {
  private final CredentialService credentialService;
  private final CredentialShareService shareService;
  private final ManagedUserService userService;
  private final WikiSpaceService wikiSpaceService;
  private final TenantAuditRecorder auditRecorder;
  private final TenantContext tenantContext;

  public CredentialController(
      CredentialService credentialService, CredentialShareService shareService,
      ManagedUserService userService, WikiSpaceService wikiSpaceService,
      TenantAuditRecorder auditRecorder, TenantContext tenantContext) {
    this.credentialService = credentialService;
    this.shareService = shareService;
    this.userService = userService;
    this.wikiSpaceService = wikiSpaceService;
    this.auditRecorder = auditRecorder;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/credentials")
  public String list(Model model) {
    populate(model);
    if (!model.containsAttribute("credentialForm")) {
      model.addAttribute("credentialForm", new CredentialForm("", CredentialType.PASSWORD, "", "", ""));
    }
    return "credentials/list";
  }

  @PostMapping("/credentials")
  public String create(
      @Valid @ModelAttribute("credentialForm") CredentialForm credentialForm,
      BindingResult bindingResult,
      Model model) {
    if (credentialForm.secret() == null || credentialForm.secret().isBlank()) {
      bindingResult.rejectValue("secret", "secret.required", "비밀은 필수입니다.");
    }
    if (!bindingResult.hasErrors()) {
      try {
        Credential created = credentialService.create(tenantContext.currentTenantId(), credentialForm);
        audit("CREDENTIAL_CREATE", created.getId(), created.getName());
        return "redirect:/credentials";
      } catch (DuplicateCredentialException exception) {
        bindingResult.rejectValue("name", "credential.duplicate", "같은 이름의 자격증명이 이미 있습니다.");
      }
    }
    populate(model);
    return "credentials/list";
  }

  @PostMapping("/credentials/{id}/delete")
  public String delete(@PathVariable UUID id) {
    credentialService.delete(tenantContext.currentTenantId(), id);
    audit("CREDENTIAL_DELETE", id, null);
    return "redirect:/credentials";
  }

  /** 크리덴셜을 사용자에게 열람 공유. */
  @PostMapping("/credentials/{id}/share")
  public String share(@PathVariable UUID id, @RequestParam UUID userId) {
    shareService.share(tenantContext.currentTenantId(), id, userId);
    audit("CREDENTIAL_SHARE", id, "user=" + userId);
    return "redirect:/credentials";
  }

  @PostMapping("/credentials/{id}/unshare")
  public String unshare(@PathVariable UUID id, @RequestParam UUID userId) {
    shareService.unshare(tenantContext.currentTenantId(), id, userId);
    audit("CREDENTIAL_UNSHARE", id, "user=" + userId);
    return "redirect:/credentials";
  }

  /** 설정법 위키 공간 연동(빈 값이면 해제). 같은 기관 공간만 허용. */
  @PostMapping("/credentials/{id}/wiki")
  public String linkWiki(@PathVariable UUID id, @RequestParam(required = false) UUID wikiSpaceId) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID valid = (wikiSpaceId != null
        && wikiSpaceService.findAll(tenantId).stream().noneMatch(s -> s.getId().equals(wikiSpaceId)))
        ? null : wikiSpaceId;
    credentialService.linkWiki(tenantId, id, valid);
    audit("CREDENTIAL_LINK_WIKI", id, null);
    return "redirect:/credentials";
  }

  private void populate(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    model.addAttribute("credentials", credentialService.findAll(tenantId));
    model.addAttribute("types", CredentialType.values());
    java.util.List<ManagedUser> users = userService.findByTenant(tenantId);
    model.addAttribute("tenantUsers", users);
    model.addAttribute("spaces", wikiSpaceService.findAll(tenantId));
    Map<UUID, String> userNames = new HashMap<>();
    for (ManagedUser u : users) {
      userNames.put(u.getId(), u.getName());
    }
    model.addAttribute("userNames", userNames);
    Map<UUID, Set<UUID>> shares = new HashMap<>();
    for (Credential c : credentialService.findAll(tenantId)) {
      shares.put(c.getId(), shareService.sharedUserIds(tenantId, c.getId()));
    }
    model.addAttribute("shares", shares);
    Map<UUID, String> spaceNames = new HashMap<>();
    for (WikiSpace s : wikiSpaceService.findAll(tenantId)) {
      spaceNames.put(s.getId(), s.getName());
    }
    model.addAttribute("spaceNames", spaceNames);
    model.addAttribute("page", "credentials");
    model.addAttribute("pageTitle", "자격증명");
    model.addAttribute("projectName", "MOA");
  }

  /** 특권 행위 기록. 정책(행위자 없으면 미기록 등)은 TenantAuditRecorder에 있다. */
  private void audit(String action, UUID targetId, String message) {
    auditRecorder.record("Credential", action, targetId, message);
  }
}
