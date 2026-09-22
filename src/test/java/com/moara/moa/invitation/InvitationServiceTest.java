package com.moara.moa.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupForm;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.group.AccessGroupStatus;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 초대: 다중 발송, 수락 시 계정(아이디=이메일, 활성)+부서 배정, 토큰 재사용 차단, 중복 스킵. */
@SpringBootTest
@ActiveProfiles("test")
class InvitationServiceTest {
  @Autowired private InvitationService invitationService;
  @Autowired private AccessGroupService groupService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void inviteThenAcceptCreatesActiveUserInGroup() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("초대사", "IV" + System.nanoTime()));
    UUID t = tenant.getId();
    AccessGroup group = groupService.create(t, new AccessGroupForm("영업팀", null, AccessGroupStatus.ACTIVE, null));
    String email = "invitee" + System.nanoTime() + "@company.com";

    List<InvitationService.InviteResult> results = invitationService.invite(
        t, null, List.of(email, "  ", email), null, group.getId(), Set.of(UserRole.USER), null, "https://moa.example");

    // 같은 이메일 중복·빈 값은 접혀 1건만.
    assertThat(results).hasSize(1);
    assertThat(results.get(0).skipped()).isFalse();
    String token = tokenFrom(results.get(0).link());

    // 수락 → 계정 생성(아이디=이메일, 활성) + 부서 배정.
    ManagedUser user = invitationService.accept(token, "홍길동", "010-1234-5678", "safe-password-123");
    // 로그인 키는 이메일. username은 표시 이름(수락 시 입력한 이름)이다.
    assertThat(user.getUsername()).isEqualTo("홍길동");
    assertThat(user.getEmail()).isEqualTo(email);
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(groupService.findMembers(t, group.getId()))
        .anyMatch(m -> m.getUserId().equals(user.getId()));

    // 토큰 재사용 차단(이미 수락됨).
    assertThatThrownBy(() -> invitationService.accept(token, "재사용", "010", "safe-password-123"))
        .isInstanceOf(InvitationInvalidException.class);

    // 이미 계정이 있는 이메일 재초대 → 스킵.
    var again = invitationService.invite(t, null, List.of(email), null, null, Set.of(UserRole.USER), null, "https://moa.example");
    assertThat(again).hasSize(1);
    assertThat(again.get(0).skipped()).isTrue();
  }

  private String tokenFrom(String link) {
    return link.substring(link.lastIndexOf("/invite/") + "/invite/".length());
  }
}
