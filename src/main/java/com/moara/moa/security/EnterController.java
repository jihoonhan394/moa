package com.moara.moa.security;

import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantNotFoundException;
import com.moara.moa.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 진입 1단계: 기관(테넌시) 코드 입력 → 검증 → 세션 저장 → 로그인(2단계)으로 이동.
 * 두레이 방식(기관 선택 후 그 기관 사용자로 로그인)을 구현한다.
 */
@Controller
public class EnterController {
  private final TenantService tenantService;

  public EnterController(TenantService tenantService) {
    this.tenantService = tenantService;
  }

  @GetMapping("/enter")
  public String enter() {
    return "enter";
  }

  @PostMapping("/enter")
  public String selectTenant(
      @RequestParam(required = false) String code, HttpServletRequest request, Model model) {
    String normalized = code == null ? "" : code.trim().toUpperCase();
    if (normalized.isEmpty()) {
      model.addAttribute("error", "기관 코드를 입력하세요.");
      return "enter";
    }
    try {
      Tenant tenant = tenantService.getByCode(normalized);
      if (!tenant.isActive()) {
        model.addAttribute("error", "비활성화된 기관입니다. 관리자에게 문의하세요.");
        model.addAttribute("code", normalized);
        return "enter";
      }
      if (tenant.isExpired(LocalDate.now())) {
        model.addAttribute("error", "구독이 만료된 기관입니다. 관리자에게 문의하세요.");
        model.addAttribute("code", normalized);
        return "enter";
      }
      EntrySession.selectTenant(request, tenant);
      return "redirect:/login";
    } catch (TenantNotFoundException exception) {
      model.addAttribute("error", "존재하지 않는 기관 코드입니다.");
      model.addAttribute("code", normalized);
      return "enter";
    }
  }
  // 플랫폼(운영자) 진입은 공개 /enter에 노출하지 않는다. 은닉 경로는 PlatformEntryFilter가 담당한다.
}
