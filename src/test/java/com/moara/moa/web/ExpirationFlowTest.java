package com.moara.moa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.expiration.ExpirationRow;
import com.moara.moa.expiration.ExpirationService;
import com.moara.moa.inventory.InventoryItemForm;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.inventory.InventoryItemType;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 만료 대시보드: 인벤토리 만료 집계(D-day 부호)·임박순 정렬·관리자 게이팅. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExpirationFlowTest {
  private static final UUID MOA = Tenant.DEFAULT_TENANT_ID;

  @Autowired private MockMvc mockMvc;
  @Autowired private ExpirationService expirationService;
  @Autowired private InventoryItemService inventoryService;
  @Autowired private ManagedUserService userService;

  @Test
  void aggregatesInventoryExpiryOverdueFirst() {
    String soon = "라이선스-" + System.nanoTime();
    String overdue = "만료-" + System.nanoTime();
    inventoryService.create(MOA, new InventoryItemForm(
        soon, InventoryItemType.SOFTWARE, "SW", null, LocalDate.now().plusDays(5), null));
    inventoryService.create(MOA, new InventoryItemForm(
        overdue, InventoryItemType.SOFTWARE, "SW", null, LocalDate.now().minusDays(3), null));

    List<ExpirationRow> rows = expirationService.findAll(MOA);
    ExpirationRow soonRow = rows.stream().filter(r -> r.label().equals(soon)).findFirst().orElseThrow();
    ExpirationRow overdueRow = rows.stream().filter(r -> r.label().equals(overdue)).findFirst().orElseThrow();

    assertEquals(5, soonRow.daysLeft());
    assertEquals(-3, overdueRow.daysLeft());
    assertTrue(rows.indexOf(overdueRow) < rows.indexOf(soonRow), "임박(만료 지난 것) 먼저 정렬");
  }

  @Test
  void managersCanViewUserCannot() throws Exception {
    ManagedUser u = user();
    mockMvc.perform(get("/expirations").with(authentication(auth(u, "ROLE_ASSET_MANAGER"))))
        .andExpect(status().isOk());
    mockMvc.perform(get("/expirations").with(authentication(auth(u, "ROLE_TENANT_ADMIN"))))
        .andExpect(status().isOk());
    mockMvc.perform(get("/expirations").with(authentication(auth(u, "ROLE_USER"))))
        .andExpect(status().isForbidden());
  }

  private ManagedUser user() {
    String username = "u" + System.nanoTime();
    return userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user, String role) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", List.of(new SimpleGrantedAuthority(role)));
  }
}
