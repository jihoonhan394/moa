package com.moara.moa.web;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.security.TenantContext;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.DuplicateTenantException;
import com.moara.moa.tenant.FeatureModule;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantForm;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.DuplicateManagedUserException;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserNotFoundException;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserRole;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 플랫폼(SYSTEM_ADMIN) 전용 기관(테넌시) 관리. 목록/등록/활성·비활성 + 기관 상세(구독·기능·대표 관리자).
 * 접근 제어는 SecurityConfig({@code /admin/**})가 SYSTEM_ADMIN으로 강제한다. 기관 관리는 전역(GLOBAL) 감사로 남긴다.
 */
@Controller
public class TenantController {
  private final TenantService tenantService;
  private final ManagedUserService userService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public TenantController(
      TenantService tenantService,
      ManagedUserService userService,
      AuditLogService auditLogService,
      TenantContext tenantContext) {
    this.tenantService = tenantService;
    this.userService = userService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/admin/tenants")
  public String list(Model model) {
    model.addAttribute("tenants", tenantService.findAll());
    model.addAttribute("today", LocalDate.now());
    if (!model.containsAttribute("tenantForm")) {
      model.addAttribute("tenantForm", new TenantForm("", ""));
    }
    return "admin/tenants/list";
  }

  @PostMapping("/admin/tenants")
  public String create(
      @Valid @ModelAttribute("tenantForm") TenantForm tenantForm,
      BindingResult bindingResult,
      Model model) {
    if (!bindingResult.hasErrors()) {
      try {
        String code = tenantForm.code().trim().toUpperCase();
        Tenant created =
            tenantService.createTenant(new CreateTenantCommand(tenantForm.name(), code));
        audit("TENANT_CREATE", created.getId(), created.getCode() + " / " + created.getName());
        return "redirect:/admin/tenants/" + created.getId();
      } catch (DuplicateTenantException exception) {
        bindingResult.rejectValue("code", "tenant.duplicate", "같은 코드의 기관이 이미 있습니다.");
      }
    }
    model.addAttribute("tenants", tenantService.findAll());
    return "admin/tenants/list";
  }

  @GetMapping("/admin/tenants/{id}")
  public String detail(@PathVariable UUID id, Model model) {
    populateDetail(model, tenantService.getById(id));
    return "admin/tenants/detail";
  }

  @PostMapping("/admin/tenants/{id}")
  public String updateDetail(
      @PathVariable UUID id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate subscriptionStart,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate subscriptionEnd,
      @RequestParam(required = false, defaultValue = "false") boolean unlimited,
      @RequestParam(required = false) Set<FeatureModule> features) {
    // 무제한 체크 시 기간 자체를 저장하지 않는다(시작일·만기일 모두 null).
    LocalDate start = unlimited ? null : subscriptionStart;
    LocalDate end = unlimited ? null : subscriptionEnd;
    tenantService.updateDetail(id, start, end, features == null ? Set.of() : features);
    audit("TENANT_UPDATE", id, "구독/기능 갱신");
    return "redirect:/admin/tenants/" + id;
  }

  @PostMapping("/admin/tenants/{id}/admins")
  public String provisionAdmin(
      @PathVariable UUID id,
      @RequestParam String username,
      @RequestParam String name,
      @RequestParam String email,
      @RequestParam String phone,
      @RequestParam String password,
      @RequestParam(required = false) String passwordConfirm,
      Model model) {
    Tenant tenant = tenantService.getById(id);
    try {
      if (password == null || password.length() < 8) {
        throw new IllegalArgumentException("short");
      }
      if (email == null || email.isBlank() || phone == null || phone.isBlank()) {
        throw new IllegalArgumentException("contact");
      }
      if (!password.equals(passwordConfirm)) {
        populateDetail(model, tenant);
        model.addAttribute("adminError", "비밀번호가 일치하지 않습니다.");
        return "admin/tenants/detail";
      }
      ManagedUser created = userService.createTenantAdmin(tenant.getId(), username, name, email, phone, password);
      audit("TENANT_ADMIN_CREATE", created.getId(), tenant.getCode() + " / " + created.getUsername());
      return "redirect:/admin/tenants/" + id;
    } catch (DuplicateManagedUserException exception) {
      populateDetail(model, tenant);
      model.addAttribute("adminError", "이미 사용 중인 아이디 또는 이메일입니다.");
      return "admin/tenants/detail";
    } catch (IllegalArgumentException exception) {
      populateDetail(model, tenant);
      model.addAttribute("adminError", "아이디/이름/이메일/전화번호/비밀번호(8자 이상)를 확인하세요.");
      return "admin/tenants/detail";
    }
  }

  @PostMapping("/admin/tenants/{id}/admins/{userId}/update")
  public String updateAdmin(
      @PathVariable UUID id,
      @PathVariable UUID userId,
      @RequestParam String name,
      @RequestParam String email,
      @RequestParam String phone,
      @RequestParam(required = false) String password,
      @RequestParam(required = false) String passwordConfirm,
      Model model) {
    Tenant tenant = tenantService.getById(id);
    ManagedUser admin = requireTenantAdmin(tenant, userId);
    if (password != null && !password.isBlank() && !password.equals(passwordConfirm)) {
      populateDetail(model, tenant);
      model.addAttribute("adminError", "비밀번호가 일치하지 않습니다.");
      return "admin/tenants/detail";
    }
    try {
      userService.updateTenantAdmin(admin.getId(), name, email, phone, password);
      audit("TENANT_ADMIN_UPDATE", id, tenant.getCode() + " / " + admin.getUsername());
      return "redirect:/admin/tenants/" + id;
    } catch (DuplicateManagedUserException exception) {
      populateDetail(model, tenant);
      model.addAttribute("adminError", "이미 사용 중인 이메일입니다.");
      return "admin/tenants/detail";
    } catch (IllegalArgumentException exception) {
      populateDetail(model, tenant);
      model.addAttribute("adminError", "이름/이메일/전화번호/비밀번호(재설정 시 8자 이상)를 확인하세요.");
      return "admin/tenants/detail";
    }
  }

  @PostMapping("/admin/tenants/{id}/admins/{userId}/enable")
  public String enableAdmin(@PathVariable UUID id, @PathVariable UUID userId) {
    ManagedUser admin = requireTenantAdmin(tenantService.getById(id), userId);
    userService.activate(admin.getTenantId(), admin.getId());
    audit("TENANT_ADMIN_ENABLE", id, admin.getUsername());
    return "redirect:/admin/tenants/" + id;
  }

  @PostMapping("/admin/tenants/{id}/admins/{userId}/disable")
  public String disableAdmin(@PathVariable UUID id, @PathVariable UUID userId) {
    ManagedUser admin = requireTenantAdmin(tenantService.getById(id), userId);
    userService.deactivate(admin.getTenantId(), admin.getId());
    audit("TENANT_ADMIN_DISABLE", id, admin.getUsername());
    return "redirect:/admin/tenants/" + id;
  }

  @PostMapping("/admin/tenants/{id}/admins/{userId}/delete")
  public String deleteAdmin(@PathVariable UUID id, @PathVariable UUID userId, Model model) {
    Tenant tenant = tenantService.getById(id);
    ManagedUser admin = requireTenantAdmin(tenant, userId);
    try {
      userService.delete(admin.getId());
      audit("TENANT_ADMIN_DELETE", id, tenant.getCode() + " / " + admin.getUsername());
      return "redirect:/admin/tenants/" + id;
    } catch (DataIntegrityViolationException exception) {
      // 감사 로그·세션 등에서 참조 중이면 하드 삭제 불가 → 비활성으로 유도.
      populateDetail(model, tenant);
      model.addAttribute("adminError",
          "이 관리자는 활동 이력이 있어 삭제할 수 없습니다. 대신 '비활성'을 사용하세요.");
      return "admin/tenants/detail";
    }
  }

  /** 대상 사용자가 해당 기관의 TENANT_ADMIN인지 확인한다(교차기관/역할 오조작 방지). 아니면 404. */
  private ManagedUser requireTenantAdmin(Tenant tenant, UUID userId) {
    ManagedUser user = userService.findById(userId);
    if (!Objects.equals(user.getTenantId(), tenant.getId()) || !user.hasRole(UserRole.TENANT_ADMIN)) {
      throw new ManagedUserNotFoundException(userId);
    }
    return user;
  }

  @PostMapping("/admin/tenants/{id}/disable")
  public String disable(@PathVariable UUID id) {
    tenantService.disableTenant(id);
    audit("TENANT_DISABLE", id, null);
    return "redirect:/admin/tenants/" + id;
  }

  @PostMapping("/admin/tenants/{id}/enable")
  public String enable(@PathVariable UUID id) {
    tenantService.enableTenant(id);
    audit("TENANT_ENABLE", id, null);
    return "redirect:/admin/tenants/" + id;
  }

  private void populateDetail(Model model, Tenant tenant) {
    List<ManagedUser> admins = userService.findTenantAdmins(tenant.getId());
    model.addAttribute("tenant", tenant);
    model.addAttribute("allFeatures", FeatureModule.values());
    model.addAttribute("tenantAdmins", admins);
    model.addAttribute("userTotal", userService.countByTenant(tenant.getId()));
    model.addAttribute("today", LocalDate.now());
  }

  private void audit(String action, UUID targetTenantId, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordGlobalAction(
          actorId, targetTenantId, action, "Tenant", targetTenantId, AuditResult.SUCCESS, message);
    }
  }
}
