package com.moara.moa.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupForm;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.group.AccessGroupStatus;
import com.moara.moa.inventory.InventoryItem;
import com.moara.moa.inventory.InventoryItemForm;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.inventory.InventoryItemType;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.moara.moa.tenant.TenantService;

/** 부서장 위임: 이끄는 부서 소유 인벤토리는 CRUD 가능, 타 부서 소유는 스코프로 차단. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeamInventoryFlowTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;
  @Autowired private AccessGroupService groupService;
  @Autowired private InventoryItemService inventoryService;

  @Test
  void leaderManagesOwnTeamInventoryButNotOthers() throws Exception {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("팀사", "TI" + System.nanoTime()));
    UUID tenantId = tenant.getId();
    ManagedUser leader = user(tenantId);
    AccessGroup mine = groupService.create(tenantId,
        new AccessGroupForm("우리팀-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
    AccessGroup others = groupService.create(tenantId,
        new AccessGroupForm("남의팀-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
    groupService.addMember(tenantId, mine.getId(), leader.getId());
    groupService.setLeader(tenantId, mine.getId(), leader.getId(), true);

    var authn = auth(leader);

    // 내 부서 소유로 등록 → 성공, 소유팀=우리팀.
    String name = "팀노트북-" + System.nanoTime();
    mockMvc.perform(post("/team/inventory").with(authentication(authn)).with(csrf())
            .param("name", name).param("type", "PHYSICAL").param("ownerGroupId", mine.getId().toString()))
        .andExpect(status().is3xxRedirection());
    InventoryItem created = inventoryService.findAll(tenantId).stream()
        .filter(i -> i.getName().equals(name)).findFirst().orElseThrow();
    Assertions.assertThat(created.getOwnerGroupId()).isEqualTo(mine.getId());

    // 남의 부서 소유로 등록 시도 → 스코프 차단(항목 안 생김).
    String bad = "탈취시도-" + System.nanoTime();
    mockMvc.perform(post("/team/inventory").with(authentication(authn)).with(csrf())
            .param("name", bad).param("type", "PHYSICAL").param("ownerGroupId", others.getId().toString()))
        .andExpect(status().is3xxRedirection());
    Assertions.assertThat(inventoryService.findAll(tenantId)).noneMatch(i -> i.getName().equals(bad));

    // 남의 부서 소유 자산은 삭제 불가(리더 스코프 밖).
    InventoryItem foreign = inventoryService.create(tenantId, new InventoryItemForm(
        "남의자산-" + System.nanoTime(), InventoryItemType.PHYSICAL, null, null, null, null));
    inventoryService.assignOwnerGroup(tenantId, foreign.getId(), others.getId());
    mockMvc.perform(post("/team/inventory/" + foreign.getId() + "/delete")
            .with(authentication(authn)).with(csrf()))
        .andExpect(status().is3xxRedirection());
    Assertions.assertThat(inventoryService.findAll(tenantId))
        .anyMatch(i -> i.getId().equals(foreign.getId())); // 여전히 존재
  }

  private ManagedUser user(UUID tenantId) {
    String username = "lead" + System.nanoTime();
    return userService.create(tenantId,
        new UserForm(username, "부서장", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(
        principal, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }
}
