package com.moara.moa.web;

import com.moara.moa.group.AccessGroupService;
import com.moara.moa.invitation.InvitationService;
import com.moara.moa.onboarding.OnboardingService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.UserRole;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 초대 관리(기관 관리자). 다중 이메일을 부서·역할·온보딩 템플릿과 함께 초대한다. 라우팅 권한은
 * SecurityConfig(/invitations/** → TENANT_ADMIN)에서 강제. 수락은 공개 라우트 {@code /invite/**}.
 */
@Controller
public class InvitationController {
  private final InvitationService invitationService;
  private final AccessGroupService groupService;
  private final OnboardingService onboardingService;
  private final TenantContext tenantContext;

  @Value("${moa.base-url:}")
  private String configuredBaseUrl;

  public InvitationController(
      InvitationService invitationService, AccessGroupService groupService,
      OnboardingService onboardingService, TenantContext tenantContext) {
    this.invitationService = invitationService;
    this.groupService = groupService;
    this.onboardingService = onboardingService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/invitations")
  public String index(@RequestParam(required = false) UUID group, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    var groups = groupService.findAll(tenantId);
    model.addAttribute("groups", groups);
    model.addAttribute("groupNames", groups.stream()
        .collect(Collectors.toMap(g -> g.getId(), g -> g.getName())));
    model.addAttribute("templates", onboardingService.templates(tenantId));
    model.addAttribute("roleOptions", List.of(UserRole.USER, UserRole.INFRA_MANAGER, UserRole.ASSET_MANAGER));
    model.addAttribute("invitations", invitationService.list(tenantId));
    model.addAttribute("selectedGroup", group);
    model.addAttribute("page", "invitations");
    return "invitations/list";
  }

  @PostMapping("/invitations")
  public String invite(
      @RequestParam String emails,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) UUID groupId,
      @RequestParam(required = false) List<UserRole> roles,
      @RequestParam(required = false) UUID templateId,
      RedirectAttributes redirectAttributes) {
    UUID tenantId = tenantContext.currentTenantId();
    List<String> emailList = Arrays.stream(emails.split("[\\s,;]+"))
        .map(String::trim).filter(s -> !s.isEmpty()).toList();
    Set<UserRole> roleSet = roles == null || roles.isEmpty()
        ? Set.of(UserRole.USER) : roles.stream().collect(Collectors.toSet());
    var results = invitationService.invite(
        tenantId, tenantContext.currentUserId(), emailList, name, groupId, roleSet, templateId, baseUrl());
    redirectAttributes.addFlashAttribute("results", results);
    return "redirect:/invitations";
  }

  @PostMapping("/invitations/{id}/revoke")
  public String revoke(@PathVariable UUID id) {
    invitationService.revoke(tenantContext.currentTenantId(), id);
    return "redirect:/invitations";
  }

  /** 재발송: 새 링크 생성(+메일 발송 시도). 링크를 잃어버렸을 때 다시 받는다. */
  @PostMapping("/invitations/{id}/resend")
  public String resend(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    var result = invitationService.resend(tenantContext.currentTenantId(), id, baseUrl());
    redirectAttributes.addFlashAttribute("results", List.of(result));
    return "redirect:/invitations";
  }

  /** 초대 링크의 절대 URL 기준. 설정(moa.base-url) 우선, 없으면 요청(프록시 X-Forwarded 반영)에서 유도. */
  private String baseUrl() {
    if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
      return configuredBaseUrl.replaceAll("/+$", "");
    }
    return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
  }
}
