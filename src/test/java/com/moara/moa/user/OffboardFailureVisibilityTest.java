package com.moara.moa.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupForm;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.group.AccessGroupStatus;
import com.moara.moa.group.UserGroupMemberRepository;
import com.moara.moa.permission.Permission;
import com.moara.moa.permission.PermissionForm;
import com.moara.moa.permission.PermissionSetService;
import com.moara.moa.permission.PermissionStatus;
import com.moara.moa.permission.PermissionUserAssignmentRepository;
import com.moara.moa.tenant.Tenant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

/**
 * 퇴사 회수 중 핸들러 하나가 실패하면 {@link OffboardFailedException}으로 감싸져 올라오고, 전체가
 * 롤백돼 부분 회수가 남지 않는지 검증한다(설계 의도: 퇴사는 보안 행위라 부분 회수를 허용하지 않음 —
 * 권한은 지웠는데 자산은 남는 상태가 더 위험하다).
 *
 * <p>일부러 실패하는 {@link OffboardHandler}는 이 테스트 클래스 전용 {@link TestConfiguration}(중첩
 * 클래스)으로만 등록한다. Spring Boot는 테스트 클래스의 중첩 {@code @TestConfiguration}을 해당 테스트의
 * 애플리케이션 컨텍스트에만 추가하므로(다른 테스트 클래스는 별도 컨텍스트를 사용), 이 스텁 핸들러가
 * {@link UserLifecycleServiceTest} 등 다른 테스트의 회수 결과에 섞여 들어가지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class OffboardFailureVisibilityTest {
  private static final UUID MOA = Tenant.DEFAULT_TENANT_ID;

  @TestConfiguration
  static class FailingHandlerConfig {
    @Bean
    FailingOffboardHandler failingOffboardHandler() {
      return new FailingOffboardHandler();
    }
  }

  /** 항상 실패하는 회수 핸들러(테스트 전용). 클래스 단순명이 {@link OffboardFailedException#getHandlerName()}에 담긴다. */
  static class FailingOffboardHandler implements OffboardHandler {
    @Override
    public OffboardOutcome offboard(UUID tenantId, UUID userId) {
      throw new RuntimeException("의도된 회수 실패(테스트)");
    }
  }

  @Autowired private UserLifecycleService lifecycleService;
  @Autowired private ManagedUserService userService;
  @Autowired private AccessGroupService groupService;
  @Autowired private PermissionSetService permissionSetService;
  @Autowired private UserGroupMemberRepository groupMemberRepository;
  @Autowired private PermissionUserAssignmentRepository permissionAssignmentRepository;

  @Test
  void handlerFailureIsWrappedWithHandlerName() {
    ManagedUser user = createUser();

    OffboardFailedException failure = assertThrows(OffboardFailedException.class,
        () -> lifecycleService.offboard(MOA, user.getId()));

    assertEquals("FailingOffboardHandler", failure.getHandlerName());
  }

  @Test
  void handlerFailureRollsBackAllOtherRevocations() {
    ManagedUser user = createUser();
    UUID userId = user.getId();
    // 실패 핸들러 외에 회수 대상(그룹 멤버십, 직접 권한)을 미리 부여해 둔다.
    AccessGroup group = groupService.create(MOA, new AccessGroupForm(
        "g-" + System.nanoTime(), "설명", AccessGroupStatus.ACTIVE, null));
    groupService.addMember(MOA, group.getId(), userId);
    Permission permission = permissionSetService.create(MOA, new PermissionForm(
        "p-" + System.nanoTime(), null, PermissionStatus.ACTIVE));
    permissionSetService.assignToUser(MOA, permission.getId(), userId, null);

    // 사전 조건: 회수 대상 2종 모두 존재.
    assertEquals(1, groupMemberRepository.findAllByTenantIdAndUserId(MOA, userId).size());
    assertEquals(1, permissionAssignmentRepository.findAllByTenantIdAndUserId(MOA, userId).size());

    assertThrows(OffboardFailedException.class, () -> lifecycleService.offboard(MOA, userId));

    // 부분 회수 없음: 실패 핸들러와 무관한 다른 회수 대상도 그대로 남아 있어야 한다(전체 롤백).
    assertEquals(1, groupMemberRepository.findAllByTenantIdAndUserId(MOA, userId).size());
    assertEquals(1, permissionAssignmentRepository.findAllByTenantIdAndUserId(MOA, userId).size());

    // 상태도 여전히 ACTIVE — OFFBOARDED로 전이되지 않았다.
    ManagedUser reloaded = userService.findById(userId);
    assertEquals(UserStatus.ACTIVE, reloaded.getStatus());
  }

  private ManagedUser createUser() {
    String username = "u" + System.nanoTime();
    return userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
