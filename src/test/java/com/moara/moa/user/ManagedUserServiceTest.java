package com.moara.moa.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moara.moa.tenant.Tenant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ManagedUserServiceTest {
  @Autowired private ManagedUserService userService;

  @Test
  void createsUpdatesAndDisablesUser() {
    String username = "user" + System.nanoTime();
    ManagedUser created = userService.create(new UserForm(
        username, "테스트 사용자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));

    ManagedUser updated = userService.update(Tenant.DEFAULT_TENANT_ID, created.getId(), new UserForm(
        username, "수정 사용자", username + "@example.com", "", UserStatus.ACTIVE));
    assertEquals("수정 사용자", updated.getName());

    userService.disable(Tenant.DEFAULT_TENANT_ID, created.getId());
    assertEquals(UserStatus.DISABLED, userService.findById(created.getId()).getStatus());
  }

  @Test
  void newUserBelongsToDefaultTenantAsUser() {
    String username = "user" + System.nanoTime();
    ManagedUser created = userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));

    assertEquals(Tenant.DEFAULT_TENANT_ID, created.getTenantId());
    assertEquals(java.util.Set.of(UserRole.USER), created.getRoles());
  }

  /**
   * 브로드캐스트 수신자에 관리자도 포함되어야 한다. assignable()이 "관리 역할이 있으면 USER 제거"를
   * 강제하므로, 과거처럼 hasRole(USER)로 거르면 관리자가 자기 기관 공지·전체 메일을 영영 못 받는다.
   */
  @Test
  void broadcastRecipientsIncludeAdmins() {
    String admin = "adm" + System.nanoTime();
    ManagedUser created = userService.create(Tenant.DEFAULT_TENANT_ID, new UserForm(
        admin, "관리자", admin + "@example.com", "safe-password-123", UserStatus.ACTIVE));
    userService.assignRoles(Tenant.DEFAULT_TENANT_ID, created.getId(), Set.of(UserRole.TENANT_ADMIN));

    // 관리 역할 부여로 USER 롤이 제거됐는지 먼저 확인(전제 검증)
    assertEquals(Set.of(UserRole.TENANT_ADMIN),
        userService.findById(Tenant.DEFAULT_TENANT_ID, created.getId()).getRoles());

    assertTrue(userService.activeUserEmails(Tenant.DEFAULT_TENANT_ID).contains(admin + "@example.com"),
        "관리자가 브로드캐스트 수신자에서 누락됨");
  }
  /**
   * 표시 라벨 형식을 고정한다. 전에는 화면 5곳이 각자 "이름 (계정)"을 만들고 접속 이력만
   * 순서가 뒤집혀 있어, 같은 사람이 화면마다 다르게 보였다. 형식이 한 곳에 있다는 것을
   * 이 테스트가 지킨다.
   */
  @Test
  void userLabelIsNameThenUsername() {
    String username = "lbl" + System.nanoTime();
    ManagedUser created = userService.create(Tenant.DEFAULT_TENANT_ID, new UserForm(
        username, "이름있는사람", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));

    assertEquals("이름있는사람 (" + username + ")", ManagedUserService.label(created));
    assertEquals("이름있는사람 (" + username + ")",
        userService.labelsByTenant(Tenant.DEFAULT_TENANT_ID).get(created.getId()));
    assertEquals("이름있는사람",
        userService.namesByTenant(Tenant.DEFAULT_TENANT_ID).get(created.getId()));
  }

  /**
   * 맵 순서가 조회 순서(이름 오름차순)와 같아야 한다 — 이 맵이 선택 상자에 그대로 쓰인다.
   * HashMap으로 돌아가면 담당자 목록이 매번 다른 순서로 보인다.
   *
   * <p>정렬 규칙을 다시 계산해 비교하지 않는다(DB 콜레이션과 자바 비교가 어긋날 수 있다).
   * 확인할 것은 "조회한 순서를 그대로 유지하는가"다.
   */
  @Test
  void nameMapsKeepQueryOrder() {
    var expected = userService.findByTenant(Tenant.DEFAULT_TENANT_ID).stream()
        .map(ManagedUser::getId).toList();

    assertEquals(expected,
        java.util.List.copyOf(userService.namesByTenant(Tenant.DEFAULT_TENANT_ID).keySet()));
    assertEquals(expected,
        java.util.List.copyOf(userService.labelsByTenant(Tenant.DEFAULT_TENANT_ID).keySet()));
  }
}
