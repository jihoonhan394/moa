package com.moara.moa.reservation;

import com.moara.moa.audit.TenantAuditRecorder;
import com.moara.moa.security.TenantContext;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 공유자산 예약(일반 사용자 소비 화면). 예약 가능한 자산 목록 + 내 예약 + 부킹/취소. */
@Controller
public class ReservationController {
  private final ReservationService reservationService;
  private final TenantAuditRecorder auditRecorder;
  private final TenantContext tenantContext;

  public ReservationController(
      ReservationService reservationService, TenantAuditRecorder auditRecorder, TenantContext tenantContext) {
    this.reservationService = reservationService;
    this.auditRecorder = auditRecorder;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/reservations")
  public String list(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!model.containsAttribute("reservationForm")) {
      model.addAttribute("reservationForm", new ReservationForm(null, null, null, null));
    }
    java.util.List<SharedResource> resources = reservationService.bookableResources(tenantId);
    model.addAttribute("resources", resources);
    Map<UUID, String> resourceNames = new HashMap<>();
    for (SharedResource r : resources) {
      resourceNames.put(r.getId(), r.getName());
    }
    java.util.List<Reservation> mine = reservationService.findMyReservations(tenantId, tenantContext.currentUserId());
    for (Reservation r : mine) {
      resourceNames.putIfAbsent(r.getResourceId(), "(삭제된 자산)");
    }
    model.addAttribute("resourceNames", resourceNames);
    model.addAttribute("myReservations", mine);
    model.addAttribute("page", "reservations");
    model.addAttribute("pageTitle", "공유자산 예약");
    model.addAttribute("projectName", "MOA");
    return "reservations/list";
  }

  @PostMapping("/reservations")
  public String book(
      @Valid @ModelAttribute("reservationForm") ReservationForm form, BindingResult binding,
      RedirectAttributes redirect) {
    if (binding.hasErrors()) {
      redirect.addFlashAttribute("reservationError", "예약 정보를 확인하세요(자산·시작·종료 필수).");
      return "redirect:/reservations";
    }
    try {
      Reservation booked = reservationService.book(tenantContext.currentTenantId(), tenantContext.currentUserId(), form);
      audit("RESERVATION_CREATE", booked.getId(), "resource=" + form.resourceId());
      redirect.addFlashAttribute("reservationMessage", "예약되었습니다.");
    } catch (ReservationConflictException exception) {
      redirect.addFlashAttribute("reservationError", exception.getMessage());
    }
    return "redirect:/reservations";
  }

  @PostMapping("/reservations/{id}/cancel")
  public String cancel(@PathVariable UUID id, RedirectAttributes redirect) {
    try {
      reservationService.cancel(tenantContext.currentTenantId(), id, tenantContext.currentUserId());
      audit("RESERVATION_CANCEL", id, null);
      redirect.addFlashAttribute("reservationMessage", "예약을 취소했습니다.");
    } catch (AccessDeniedException exception) {
      redirect.addFlashAttribute("reservationError", "본인 예약만 취소할 수 있습니다.");
    }
    return "redirect:/reservations";
  }

  /** 특권 행위 기록. 정책(행위자 없으면 미기록 등)은 TenantAuditRecorder에 있다. */
  private void audit(String action, UUID targetId, String message) {
    auditRecorder.record("Reservation", action, targetId, message);
  }
}
