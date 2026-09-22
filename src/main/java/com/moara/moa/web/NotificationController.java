package com.moara.moa.web;

import com.moara.moa.notification.NotificationService;
import com.moara.moa.security.TenantContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/** 인앱 알림 목록/읽음 처리(로그인 사용자 본인). */
@Controller
public class NotificationController {
  private final NotificationService notificationService;
  private final TenantContext tenantContext;

  public NotificationController(NotificationService notificationService, TenantContext tenantContext) {
    this.notificationService = notificationService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/notifications")
  public String list(Model model) {
    model.addAttribute("notifications",
        notificationService.list(tenantContext.currentTenantId(), tenantContext.currentUserId()));
    model.addAttribute("page", "notifications");
    return "notifications";
  }

  @PostMapping("/notifications/read-all")
  public String readAll() {
    notificationService.markAllRead(tenantContext.currentTenantId(), tenantContext.currentUserId());
    return "redirect:/notifications";
  }
}
