package com.moara.moa.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetForm;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetStatus;
import com.moara.moa.asset.AssetType;
import com.moara.moa.connection.ConnectionSession;
import com.moara.moa.connection.ConnectionSessionService;
import com.moara.moa.connection.ConnectionStatus;
import com.moara.moa.connection.SessionProtocol;
import com.moara.moa.inventory.InventoryItem;
import com.moara.moa.inventory.InventoryItemForm;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.inventory.InventoryItemType;
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
import com.moara.moa.reservation.ReservationForm;
import com.moara.moa.reservation.ReservationService;
import com.moara.moa.reservation.SharedResource;
import com.moara.moa.reservation.SharedResourceForm;
import com.moara.moa.reservation.SharedResourceService;
import com.moara.moa.reservation.SharedResourceStatus;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.wiki.WikiAccessLevel;
import com.moara.moa.wiki.WikiSpace;
import com.moara.moa.wiki.WikiSpaceForm;
import com.moara.moa.wiki.WikiSpaceService;
import com.moara.moa.wiki.WikiSubjectType;
import com.moara.moa.solution.HealthCheckType;
import com.moara.moa.solution.ManagedSolution;
import com.moara.moa.solution.ManagedSolutionService;
import com.moara.moa.solution.SolutionAccessService;
import com.moara.moa.solution.SolutionForm;
import com.moara.moa.solution.SolutionStatus;
import com.moara.moa.solution.SolutionType;
import com.moara.moa.solution.SolutionUserAssignmentRepository;
import com.moara.moa.remote.RemoteProtocol;
import com.moara.moa.tenant.Tenant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** B작업: 퇴사 처리가 대상의 모든 접근을 실제로 회수하고 로그인을 차단하는지 검증(보안 핵심). */
@SpringBootTest
@ActiveProfiles("test")
class UserLifecycleServiceTest {
  private static final UUID MOA = Tenant.DEFAULT_TENANT_ID;

  @Autowired private UserLifecycleService lifecycleService;
  @Autowired private ManagedUserService userService;
  @Autowired private AccessGroupService groupService;
  @Autowired private AssetService assetService;
  @Autowired private PermissionSetService permissionSetService;
  @Autowired private ManagedSolutionService solutionService;
  @Autowired private SolutionAccessService solutionAccessService;
  @Autowired private UserGroupMemberRepository groupMemberRepository;
  @Autowired private PermissionUserAssignmentRepository permissionAssignmentRepository;
  @Autowired private SolutionUserAssignmentRepository solutionAssignmentRepository;
  @Autowired private InventoryItemService inventoryService;
  @Autowired private ConnectionSessionService connectionSessionService;
  @Autowired private SharedResourceService resourceService;
  @Autowired private ReservationService reservationService;
  @Autowired private WikiSpaceService wikiSpaceService;

  @Test
  void offboardRevokesAllAccessAndBlocksLogin() {
    ManagedUser user = createUser();
    UUID userId = user.getId();
    // 접근 3종을 부여: 그룹 멤버십, 직접 권한, 솔루션 배정.
    AccessGroup group = groupService.create(MOA, new AccessGroupForm(
        "g-" + System.nanoTime(), "설명", AccessGroupStatus.ACTIVE, null));
    groupService.addMember(MOA, group.getId(), userId);
    Permission permission = permissionSetService.create(MOA, new PermissionForm(
        "p-" + System.nanoTime(), null, PermissionStatus.ACTIVE));
    permissionSetService.assignToUser(MOA, permission.getId(), userId, null);
    ManagedSolution solution = createSolution();
    solutionAccessService.assignUser(MOA, solution.getId(), userId);
    // 배정 자산(인벤토리) + 활성 접속 세션.
    InventoryItem asset = inventoryService.create(MOA, new InventoryItemForm(
        "노트북-" + System.nanoTime(), InventoryItemType.PHYSICAL, "노트북", null, null, null));
    inventoryService.assign(MOA, asset.getId(), userId);
    Asset server = assetService.create(MOA, new AssetForm(
        "sess-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "192.0.2.20", 22, "", "LINUX", "t",
        AssetStatus.ACTIVE));
    ConnectionSession session = connectionSessionService.openSession(
        MOA, userId, server.getId(), SessionProtocol.SSH, "sess-" + System.nanoTime(), "127.0.0.1");
    connectionSessionService.markConnected(MOA, session.getId());
    // 향후 예약.
    SharedResource room = resourceService.create(MOA, new SharedResourceForm(
        "회의실-" + System.nanoTime(), "회의실", "3층", 6, SharedResourceStatus.ACTIVE, null));
    reservationService.book(MOA, userId, new ReservationForm(
        room.getId(), java.time.LocalDateTime.of(2030, 3, 1, 10, 0), java.time.LocalDateTime.of(2030, 3, 1, 11, 0), "회의"));
    // 위키 공간에 개인 권한.
    WikiSpace wikiSpace = wikiSpaceService.create(MOA, null, new WikiSpaceForm("퇴사위키-" + System.nanoTime(), null));
    wikiSpaceService.grant(MOA, wikiSpace.getId(), WikiSubjectType.USER, userId, WikiAccessLevel.EDIT);

    // 사전 조건: 3종 모두 존재.
    assertEquals(1, groupMemberRepository.findAllByTenantIdAndUserId(MOA, userId).size());
    assertEquals(1, permissionAssignmentRepository.findAllByTenantIdAndUserId(MOA, userId).size());
    assertEquals(1, solutionAssignmentRepository.findAllByTenantIdAndUserId(MOA, userId).size());

    OffboardResult result = lifecycleService.offboard(MOA, userId);

    // 모듈별 회수 건수(플러그인 핸들러 결과).
    assertEquals(1, revoked(result, "그룹 멤버십"));
    assertEquals(1, revoked(result, "직접 권한"));
    assertEquals(1, revoked(result, "솔루션 배정"));
    assertEquals(1, revoked(result, "배정 자산"));
    assertEquals(1, revoked(result, "활성 세션"));
    assertEquals(1, revoked(result, "예약"));
    assertEquals(1, revoked(result, "위키 개인권한"));
    assertEquals(7, result.total());

    // 배정 자산 회수 + 세션 종료 + 예약 취소.
    assertTrue(inventoryService.findAssignedTo(MOA, userId).isEmpty());
    assertEquals(ConnectionStatus.CLOSED, connectionSessionService.findById(MOA, session.getId()).getStatus());
    assertEquals(0, reservationService.findMyReservations(MOA, userId).stream()
        .filter(com.moara.moa.reservation.Reservation::isBooked).count());

    // 3종 모두 회수됨.
    assertTrue(groupMemberRepository.findAllByTenantIdAndUserId(MOA, userId).isEmpty());
    assertTrue(permissionAssignmentRepository.findAllByTenantIdAndUserId(MOA, userId).isEmpty());
    assertTrue(solutionAssignmentRepository.findAllByTenantIdAndUserId(MOA, userId).isEmpty());
    assertFalse(solutionAccessService.canControl(MOA, solution.getId(), userId));

    // 상태 = OFFBOARDED, 로그인 차단.
    ManagedUser reloaded = userService.findById(userId);
    assertEquals(UserStatus.OFFBOARDED, reloaded.getStatus());
    assertFalse(new MoaUserDetails(reloaded).isEnabled());
  }

  @Test
  void offboardWithNoGrantsSucceedsWithZeroCounts() {
    ManagedUser user = createUser();
    OffboardResult result = lifecycleService.offboard(MOA, user.getId());
    assertEquals(0, result.total());
    assertEquals(UserStatus.OFFBOARDED, userService.findById(user.getId()).getStatus());
  }

  @Test
  void disabledUserCanBeReactivated() {
    ManagedUser user = createUser();
    userService.disable(MOA, user.getId());
    assertEquals(UserStatus.DISABLED, userService.findById(user.getId()).getStatus());
    userService.activate(MOA, user.getId());
    assertEquals(UserStatus.ACTIVE, userService.findById(user.getId()).getStatus());
  }

  private long revoked(OffboardResult result, String label) {
    return result.outcomes().stream()
        .filter(outcome -> outcome.label().equals(label))
        .mapToLong(OffboardOutcome::count).sum();
  }

  private ManagedUser createUser() {
    String username = "u" + System.nanoTime();
    return userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private ManagedSolution createSolution() {
    Asset server = assetService.create(MOA, new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "192.0.2.10", 22, "", "LINUX", "t",
        AssetStatus.ACTIVE));
    String name = "sol-" + System.nanoTime();
    return solutionService.create(MOA, new SolutionForm(
        server.getId(), name, SolutionType.CUSTOM_COMMAND, name, null,
        HealthCheckType.NONE, null, SolutionStatus.ACTIVE,
        "bash start.sh", "bash stop.sh", "bash status.sh", RemoteProtocol.SSH, null));
  }
}
