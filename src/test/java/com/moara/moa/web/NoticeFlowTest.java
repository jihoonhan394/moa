package com.moara.moa.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 공지: 관리자 작성 / 전원 열람 / 사용자 쓰기 차단(403) / 교차기관 격리를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NoticeFlowTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void adminCreatesNoticeAndUserCanViewButNotWrite() throws Exception {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("공지사", "NOTI" + System.nanoTime()));
    ManagedUser admin = userService.createTenantAdmin(
        tenant.getId(), "ta" + System.nanoTime(), "관리자",
        "adm" + System.nanoTime() + "@example.com", "010-0000-0000", "safe-password-123");
    String userName = "u" + System.nanoTime();
    ManagedUser user = userService.create(tenant.getId(),
        new UserForm(userName, "사용자", userName + "@example.com", "safe-password-123", UserStatus.ACTIVE));

    String title = "점검 공지 " + System.nanoTime();
    // 관리자 작성 성공. (_pinned/_emailToUsers는 Thymeleaf 체크박스 히든 마커 — 미체크 시에도 전송됨)
    mockMvc.perform(post("/notices")
            .param("title", title).param("body", "오늘 밤 점검합니다.")
            .param("_pinned", "on").param("_emailToUsers", "on")
            .with(authentication(auth(admin))).with(csrf()))
        .andExpect(status().is3xxRedirection());

    // 일반 사용자 열람 가능(전원 열람).
    mockMvc.perform(get("/notices").with(authentication(auth(user))))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString(title)));

    // 일반 사용자 작성 차단(403).
    mockMvc.perform(post("/notices")
            .param("title", "몰래").param("body", "쓰기 시도")
            .with(authentication(auth(user))).with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void noticeIsIsolatedByTenant() throws Exception {
    Tenant tenantA = tenantService.createTenant(new CreateTenantCommand("A사", "NA" + System.nanoTime()));
    ManagedUser adminA = userService.createTenantAdmin(
        tenantA.getId(), "aa" + System.nanoTime(), "A관리자",
        "adm" + System.nanoTime() + "@example.com", "010-0000-0000", "safe-password-123");
    mockMvc.perform(post("/notices")
            .param("title", "A전용").param("body", "A 내용")
            .param("_pinned", "on").param("_emailToUsers", "on")
            .with(authentication(auth(adminA))).with(csrf()))
        .andExpect(status().is3xxRedirection());

    // 다른 기관 사용자에겐 A사의 공지가 목록에 보이지 않는다.
    Tenant tenantB = tenantService.createTenant(new CreateTenantCommand("B사", "NB" + System.nanoTime()));
    ManagedUser adminB = userService.createTenantAdmin(
        tenantB.getId(), "bb" + System.nanoTime(), "B관리자",
        "adm" + System.nanoTime() + "@example.com", "010-0000-0000", "safe-password-123");
    mockMvc.perform(get("/notices").with(authentication(auth(adminB))))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("A전용"))));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
  }
}
