package com.moara.moa.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.inventory.InventoryItem;
import com.moara.moa.inventory.InventoryItemForm;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.inventory.InventoryItemType;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 인벤토리는 자산 관리자(ASSET_MANAGER) 전용 — 인프라 관리자·일반 사용자는 차단. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryFlowTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ManagedUserService userService;
  @Autowired private InventoryItemService inventoryService;

  @Test
  void assetManagerCanListAndCreate() throws Exception {
    ManagedUser mgr = user();
    String name = "노트북-" + System.nanoTime();

    mockMvc.perform(post("/inventory").with(authentication(auth(mgr, "ROLE_ASSET_MANAGER"))).with(csrf())
            .param("name", name).param("category", "실물 / 사무기기 / 컴퓨터"))
        .andExpect(status().is3xxRedirection());

    mockMvc.perform(get("/inventory").with(authentication(auth(mgr, "ROLE_ASSET_MANAGER"))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(name)));
  }

  @Test
  void infraManagerAndUserAreForbidden() throws Exception {
    ManagedUser mgr = user();
    mockMvc.perform(get("/inventory").with(authentication(auth(mgr, "ROLE_INFRA_MANAGER"))))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/inventory").with(authentication(auth(mgr, "ROLE_USER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("⚠️ PC 등록도 없는 분류를 거부한다 — 최상위만 맞으면 통과하던 구멍")
  void 없는_분류로는_등록되지_않는다() throws Exception {
    ManagedUser mgr = user();
    String name = "유령자산-" + System.nanoTime();

    mockMvc.perform(post("/inventory").with(authentication(auth(mgr, "ROLE_ASSET_MANAGER")))
            .with(csrf())
            .param("name", name).param("category", "실물 / 없는분류 / 더없음"))
        .andExpect(status().isOk())   // 리다이렉트가 아니라 폼으로 되돌아온다
        .andExpect(content().string(containsString("없는 분류입니다")));

    // 저장되지 않았다 — 저장됐다면 그 자산은 어느 분류 필터에도 안 걸린다.
    assertThat(inventoryService.findAll(Tenant.DEFAULT_TENANT_ID))
        .noneMatch(item -> item.getName().equals(name));
  }

  @Test
  @DisplayName("PC 등록도 짧은 이름을 전체 경로로 바꿔 저장한다 — CSV 임포트와 같은 규칙")
  void 짧은_이름은_경로로_저장된다() throws Exception {
    ManagedUser mgr = user();
    String name = "모니터자산-" + System.nanoTime();

    mockMvc.perform(post("/inventory").with(authentication(auth(mgr, "ROLE_ASSET_MANAGER")))
            .with(csrf())
            .param("name", name).param("category", "모니터"))
        .andExpect(status().is3xxRedirection());

    assertThat(inventoryService.findAll(Tenant.DEFAULT_TENANT_ID))
        .filteredOn(item -> item.getName().equals(name))
        .singleElement()
        .satisfies(item -> {
          assertThat(item.getCategory()).isEqualTo("실물 / 사무기기 / 모니터");
          assertThat(item.getType()).isEqualTo(InventoryItemType.PHYSICAL);
        });
  }

  @Test
  @DisplayName("⚠️ 수정도 없는 분류를 거부한다 — 등록만 막으면 수정으로 들어온다")
  void 수정으로도_없는_분류를_넣을_수_없다() throws Exception {
    ManagedUser mgr = user();
    InventoryItem item = inventoryService.create(Tenant.DEFAULT_TENANT_ID,
        new InventoryItemForm("수정대상-" + System.nanoTime(), InventoryItemType.PHYSICAL,
            "실물 / 가구", null, null, null));

    mockMvc.perform(post("/inventory/" + item.getId())
            .with(authentication(auth(mgr, "ROLE_ASSET_MANAGER"))).with(csrf())
            .param("name", item.getName()).param("category", "실물 / 없는분류"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("없는 분류입니다")));

    assertThat(inventoryService.findById(Tenant.DEFAULT_TENANT_ID, item.getId()).getCategory())
        .as("거부됐으면 원래 분류가 그대로여야 한다")
        .isEqualTo("실물 / 가구");
  }

  private ManagedUser user() {
    String username = "u" + System.nanoTime();
    return userService.create(new UserForm(
        username, "관리자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user, String role) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", List.of(new SimpleGrantedAuthority(role)));
  }
}
