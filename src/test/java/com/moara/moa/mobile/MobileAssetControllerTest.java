package com.moara.moa.mobile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.category.CategoryDomain;
import com.moara.moa.category.CategoryService;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.inventory.tag.AssetTag;
import com.moara.moa.inventory.tag.AssetTagService;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileAssetControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AssetTagService tagService;
  @Autowired private InventoryItemService inventoryService;
  @Autowired private ManagedUserService userService;
  @Autowired private CategoryService categoryService;
  @Autowired private TenantService tenantService;

  @Test
  @DisplayName("비어 있는 번호를 찍으면 등록 화면이 뜬다 — 사용자가 모드를 고르지 않는다")
  void 미부착_번호는_등록_화면() throws Exception {
    ManagedUser actor = assetManager();
    AssetTag tag = issueOne(actor);

    mockMvc.perform(get("/m/t/" + tag.getCode()).with(authentication(auth(actor))))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("자산 등록")))
        .andExpect(content().string(Matchers.containsString(tag.getCode())));
  }

  @Test
  @DisplayName("등록하면 그 번호가 자산에 붙고, 같은 번호가 이제 조회 화면이 된다")
  void 등록하면_번호가_붙고_조회가_된다() throws Exception {
    ManagedUser actor = assetManager();
    AssetTag tag = issueOne(actor);
    String category = leafCategory(actor.getTenantId());
    String name = "현장등록-" + System.nanoTime();

    mockMvc.perform(post("/m/t/" + tag.getCode()).with(authentication(auth(actor))).with(csrf())
            .param("name", name)
            .param("category", category))
        .andExpect(redirectedUrl("/m/t/" + tag.getCode()));

    assertThat(tagService.resolve(actor.getTenantId(), tag.getCode()).isAttached()).isTrue();
    assertThat(inventoryService.findAll(actor.getTenantId()))
        .anyMatch(item -> item.getName().equals(name));

    mockMvc.perform(get("/m/t/" + tag.getCode()).with(authentication(auth(actor))))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString(name)));
  }

  @Test
  @DisplayName("⚠️ 다른 기관의 번호는 404다 — 있다는 사실조차 알려 주지 않는다")
  void 다른_기관_번호는_404() throws Exception {
    ManagedUser mine = assetManager();
    Tenant other = tenantService.createTenant(new CreateTenantCommand(
        "타기관-" + System.nanoTime(), "T" + Long.toString(System.nanoTime(), 36).toUpperCase()));
    AssetTag foreign = tagService.issue(other.getId(), 1, null).get(0);

    mockMvc.perform(get("/m/t/" + foreign.getCode()).with(authentication(auth(mine))))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/m/scan?notFound=" + foreign.getCode()));

    // 그리고 그 번호는 여전히 상대 기관 것이다(우리 기관이 가로채지 않았다).
    assertThat(tagService.resolve(other.getId(), foreign.getCode()).getTenantId())
        .isEqualTo(other.getId());
  }

  @Test
  @DisplayName("형식이 틀린 번호는 오류가 아니라 '없는 번호'로 돌려보낸다")
  void 형식이_틀리면_스캔_화면으로() throws Exception {
    ManagedUser actor = assetManager();

    mockMvc.perform(get("/m/t/MOA-0000-0001").with(authentication(auth(actor))))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/m/scan?notFound=MOA-0000-0001"));
  }

  @Test
  @DisplayName("분류를 안 고르면 등록되지 않는다 — 유형(실물/SW)이 거기서 나온다")
  void 분류가_없으면_등록_실패() throws Exception {
    ManagedUser actor = assetManager();
    AssetTag tag = issueOne(actor);

    mockMvc.perform(post("/m/t/" + tag.getCode()).with(authentication(auth(actor))).with(csrf())
            .param("name", "분류없음-" + System.nanoTime()))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("분류를 선택하세요")));

    assertThat(tagService.resolve(actor.getTenantId(), tag.getCode()).isAttached()).isFalse();
  }

  @Test
  @DisplayName("⚠️ 한 자산에 번호를 두 개 붙일 수 없다 — 막지 않으면 DB 제약이 터져 500 이 나간다")
  void 한_자산에_번호는_하나() throws Exception {
    ManagedUser actor = assetManager();
    AssetTag first = issueOne(actor);
    AssetTag second = issueOne(actor);
    String category = leafCategory(actor.getTenantId());

    // 번호 하나를 붙여 둔 자산을 만든다
    mockMvc.perform(post("/m/t/" + first.getCode()).with(authentication(auth(actor))).with(csrf())
            .param("name", "중복시험-" + System.nanoTime()).param("category", category))
        .andExpect(redirectedUrl("/m/t/" + first.getCode()));
    UUID itemId = tagService.resolve(actor.getTenantId(), first.getCode()).getItemId();

    // 같은 자산에 두 번째 번호 — 500 이 아니라 안내로 돌아와야 한다
    mockMvc.perform(post("/m/t/" + second.getCode() + "/attach")
            .with(authentication(auth(actor))).with(csrf())
            .param("itemId", itemId.toString()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/m/t/" + second.getCode() + "/attach?already=" + first.getCode()));

    // 두 번째 번호는 여전히 비어 있다
    assertThat(tagService.resolve(actor.getTenantId(), second.getCode()).isAttached()).isFalse();
  }

  @Test
  @DisplayName("⚠️ 없는 분류로는 등록되지 않는다 — 최상위만 맞으면 통과하던 것")
  void 없는_분류는_거부한다() throws Exception {
    ManagedUser actor = assetManager();
    AssetTag tag = issueOne(actor);

    mockMvc.perform(post("/m/t/" + tag.getCode()).with(authentication(auth(actor))).with(csrf())
            .param("name", "분류시험-" + System.nanoTime())
            .param("category", "실물 / 없는분류 / 더없음"))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("없는 분류입니다")));

    assertThat(tagService.resolve(actor.getTenantId(), tag.getCode()).isAttached()).isFalse();
  }

  @Test
  @DisplayName("홈과 내 자산 화면이 렌더링된다")
  void 홈과_내자산이_뜬다() throws Exception {
    ManagedUser actor = assetManager();

    mockMvc.perform(get("/m").with(authentication(auth(actor)))).andExpect(status().isOk());
    mockMvc.perform(get("/m/me").with(authentication(auth(actor)))).andExpect(status().isOk());
    mockMvc.perform(get("/m/scan").with(authentication(auth(actor)))).andExpect(status().isOk());
  }

  private AssetTag issueOne(ManagedUser actor) {
    return tagService.issue(actor.getTenantId(), 1, actor.getId()).get(0);
  }

  /** 등록에 쓸 잎 카테고리 경로. 없으면 만든다. */
  private String leafCategory(UUID tenantId) {
    List<CategoryService.CategoryNode> tree = categoryService.assetTree(tenantId);
    return tree.stream()
        .filter(node -> !node.hasChildren() && node.path().startsWith("실물"))
        .map(CategoryService.CategoryNode::path)
        .findFirst()
        .orElseGet(() -> {
          categoryService.create(tenantId, CategoryDomain.ASSET, "현장시험", rootId(tenantId, "실물"));
          return categoryService.assetTree(tenantId).stream()
              .filter(node -> node.name().equals("현장시험"))
              .map(CategoryService.CategoryNode::path)
              .findFirst()
              .orElseThrow();
        });
  }

  private UUID rootId(UUID tenantId, String name) {
    return categoryService.assetTree(tenantId).stream()
        .filter(node -> node.depth() == 0 && node.name().equals(name))
        .map(CategoryService.CategoryNode::id)
        .findFirst()
        .orElseThrow();
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    return new UsernamePasswordAuthenticationToken(new MoaUserDetails(user), "",
        List.of(new SimpleGrantedAuthority("ROLE_ASSET_MANAGER")));
  }

  private ManagedUser assetManager() {
    String username = "field" + System.nanoTime();
    return userService.create(new UserForm(
        username, "현장" + System.nanoTime(), username + "@example.com",
        "safe-password-123", UserStatus.ACTIVE));
  }
}
