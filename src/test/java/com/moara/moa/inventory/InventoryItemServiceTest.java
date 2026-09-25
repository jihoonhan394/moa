package com.moara.moa.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 인벤토리 CRUD + 배정/회수/폐기 + 퇴사 일괄 회수 로직 검증. */
@SpringBootTest
@ActiveProfiles("test")
class InventoryItemServiceTest {
  private static final UUID MOA = Tenant.DEFAULT_TENANT_ID;

  @Autowired private InventoryItemService service;
  @Autowired private ManagedUserService userService;

  @Test
  void createAssignReclaimRetireLifecycle() {
    InventoryItem item = service.create(MOA, form("노트북-" + System.nanoTime(), InventoryItemType.PHYSICAL));
    assertEquals(InventoryItemStatus.AVAILABLE, item.getStatus());

    UUID userId = user().getId();
    InventoryItem assigned = service.assign(MOA, item.getId(), userId);
    assertEquals(InventoryItemStatus.ASSIGNED, assigned.getStatus());
    assertEquals(userId, assigned.getAssignedUserId());
    assertEquals(1, service.findAssignedTo(MOA, userId).size());

    InventoryItem reclaimed = service.reclaim(MOA, item.getId());
    assertEquals(InventoryItemStatus.AVAILABLE, reclaimed.getStatus());
    assertNull(reclaimed.getAssignedUserId());

    InventoryItem retired = service.retire(MOA, item.getId());
    assertEquals(InventoryItemStatus.RETIRED, retired.getStatus());
  }

  @Test
  void duplicateNameRejected() {
    String name = "중복-" + System.nanoTime();
    service.create(MOA, form(name, InventoryItemType.SOFTWARE));
    assertThrows(DuplicateInventoryItemException.class,
        () -> service.create(MOA, form(name, InventoryItemType.SOFTWARE)));
  }

  /**
   * 퇴사 반납 요청은 건수를 돌려주고 상태를 '반납 대기'로 바꾼다.
   *
   * <p>배정은 <b>풀지 않는다</b> — 풀면 "누구에게 받아야 하나"가 사라진다. 전에 이 테스트는
   * 배정이 0건이 되는 것을 검증했는데, 그건 물건을 보지도 않고 회수됐다고 적던 동작이었다.
   */
  @Test
  void requestReturnFromMarksPendingWithoutClearingHolder() {
    UUID userId = user().getId();
    InventoryItem a = service.create(MOA, form("a-" + System.nanoTime(), InventoryItemType.PHYSICAL));
    InventoryItem b = service.create(MOA, form("b-" + System.nanoTime(), InventoryItemType.SOFTWARE));
    service.assign(MOA, a.getId(), userId);
    service.assign(MOA, b.getId(), userId);

    long requested = service.requestReturnFrom(MOA, userId);

    assertEquals(2, requested);
    assertEquals(2, service.findAssignedTo(MOA, userId).size());
    assertEquals(InventoryItemStatus.RETURN_PENDING, service.findById(MOA, a.getId()).getStatus());
    assertEquals(InventoryItemStatus.RETURN_PENDING, service.findById(MOA, b.getId()).getStatus());
  }

  private InventoryItemForm form(String name, InventoryItemType type) {
    return new InventoryItemForm(name, type, "카테고리", "SN-1", LocalDate.of(2027, 1, 1), "비고");
  }

  private ManagedUser user() {
    String username = "u" + System.nanoTime();
    return userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
