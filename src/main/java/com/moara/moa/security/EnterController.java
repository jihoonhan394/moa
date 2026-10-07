package com.moara.moa.security;

import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantNotFoundException;
import com.moara.moa.tenant.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 진입 1단계: 기관(테넌시) 코드 입력 → 검증 → 세션 저장 → 로그인(2단계)으로 이동.
 * 두레이 방식(기관 선택 후 그 기관 사용자로 로그인)을 구현한다.
 *
 * <p>기억해 둔 기관이 있으면 이 단계를 <b>건너뛴다</b>({@link RememberedTenant}).
 * 한 회사 직원이 매일 같은 코드를 치는 것이 하루에 몇 번씩 겪는 마찰이었다.
 */
@Controller
public class EnterController {
  private final TenantService tenantService;
  private final RememberedTenant rememberedTenant;

  public EnterController(TenantService tenantService, RememberedTenant rememberedTenant) {
    this.tenantService = tenantService;
    this.rememberedTenant = rememberedTenant;
  }

  /**
   * 기관 선택 화면.
   *
   * <p>기억해 둔 기관이 살아 있으면 바로 2단계로 보낸다. {@code change} 가 붙어 오면
   * 기억을 <b>지우고</b> 화면을 보여 준다 — 로그인 화면의 "다른 기관으로"가 그 경로다.
   * 그 길이 없으면 공용 PC 에서 남의 기관에 갇힌다.
   */
  @GetMapping("/enter")
  public String enter(
      @RequestParam(required = false) String change,
      HttpServletRequest request, HttpServletResponse response, Model model) {
    if (change != null) {
      rememberedTenant.forget(response);
      return "enter";
    }
    Optional<Tenant> remembered = rememberedTenant.read(request).flatMap(this::usableTenant);
    if (remembered.isPresent()) {
      EntrySession.selectTenant(request, remembered.get());
      model.addAttribute("remembered", true);
      return "redirect:/login";
    }
    // 가리키는 기관이 없거나 꺼져 있으면 조용히 지운다. 여기서 오류를 내면 사용자는
    // 자기가 뭘 잘못했는지 알 수 없다 — 기억은 편의 기능이고 실패해도 평소 화면이면 된다.
    rememberedTenant.read(request).ifPresent(code -> rememberedTenant.forget(response));
    return "enter";
  }

  @PostMapping("/enter")
  public String selectTenant(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String remember,
      HttpServletRequest request, HttpServletResponse response, Model model) {
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
      // 체크를 해제하고 들어왔으면 기억을 끈다 — 끌 길이 체크박스 하나뿐이면 안 된다.
      if (remember != null) {
        rememberedTenant.remember(response, tenant.getCode());
      } else {
        rememberedTenant.forget(response);
      }
      return "redirect:/login";
    } catch (TenantNotFoundException exception) {
      model.addAttribute("error", "존재하지 않는 기관 코드입니다.");
      model.addAttribute("code", normalized);
      return "enter";
    }
  }

  /** 기억된 코드가 지금도 쓸 수 있는 기관인지. 꺼졌거나 만료됐으면 비어 있다. */
  private Optional<Tenant> usableTenant(String code) {
    try {
      Tenant tenant = tenantService.getByCode(code.trim().toUpperCase());
      boolean usable = tenant.isActive() && !tenant.isExpired(LocalDate.now());
      return usable ? Optional.of(tenant) : Optional.empty();
    } catch (TenantNotFoundException exception) {
      return Optional.empty();
    }
  }
  // 플랫폼(운영자) 진입은 공개 /enter에 노출하지 않는다. 은닉 경로는 PlatformEntryFilter가 담당한다.
}
