package com.moara.moa.category;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CategoryServiceTest {
  @Autowired private CategoryService categoryService;
  @Autowired private TenantService tenantService;

  // 다른 테스트 클래스가 이미 MOA(DEFAULT_TENANT_ID)의 카테고리를 시드했을 수 있어(실행 순서 비보장, H2가
  // 테스트 전체 실행 동안 유지됨), 빈 상태/개수를 전제하는 검증은 매번 새로 만든 기관으로 격리한다.

  @Test
  void seedsAssetDefaultsAndIsIdempotent() {
    Tenant tenant = newTenant("Seed");

    List<CategoryService.CategoryNode> first = categoryService.tree(tenant.getId(), CategoryDomain.ASSET);

    assertTrue(first.stream().anyMatch(n -> n.parentId() == null && n.name().equals("실물")));
    assertTrue(first.stream().anyMatch(n -> n.parentId() == null && n.name().equals("SW")));
    assertTrue(first.stream().anyMatch(n -> n.name().equals("사무기기")));
    assertTrue(first.stream().anyMatch(n -> n.name().equals("컴퓨터")));
    assertTrue(first.stream().anyMatch(n -> n.name().equals("포토샵")));

    // ensureDefaults는 도메인에 카테고리가 하나라도 있으면 재시드하지 않는다(멱등).
    List<CategoryService.CategoryNode> second = categoryService.tree(tenant.getId(), CategoryDomain.ASSET);
    assertEquals(first.size(), second.size());
  }

  @Test
  void treeBuildsPathWithPathSeparator() {
    Tenant tenant = newTenant("Path");
    List<CategoryService.CategoryNode> nodes = categoryService.tree(tenant.getId(), CategoryDomain.ASSET);

    CategoryService.CategoryNode root = findByName(nodes, "실물");
    assertEquals("실물", root.path());
    assertEquals(0, root.depth());
    assertTrue(root.hasChildren());

    CategoryService.CategoryNode computer = findByName(nodes, "컴퓨터");
    assertEquals("실물" + CategoryService.PATH_SEP + "사무기기" + CategoryService.PATH_SEP + "컴퓨터",
        computer.path());
    assertEquals(2, computer.depth());
    assertFalse(computer.hasChildren());
  }

  @Test
  void rejectsDuplicateSiblingNameIgnoringCaseButAllowsSameNameUnderDifferentParent() {
    Tenant tenant = newTenant("Dup");
    String name = "장비-" + System.nanoTime();

    categoryService.create(tenant.getId(), CategoryDomain.SHARED_RESOURCE, name, null);

    // 같은 부모(루트, parentId=null) 아래 대소문자만 다른 이름은 거부.
    assertThrows(DuplicateCategoryException.class,
        () -> categoryService.create(tenant.getId(), CategoryDomain.SHARED_RESOURCE, name.toUpperCase(), null));

    // 다른 부모 아래라면 같은 이름이라도 허용(형제 범위 유일성이지 도메인 전역 유일성이 아님).
    String otherParent = "다른부모-" + System.nanoTime();
    categoryService.create(tenant.getId(), CategoryDomain.SHARED_RESOURCE, otherParent, null);
    UUID otherParentId = idOf(tenant.getId(), CategoryDomain.SHARED_RESOURCE, otherParent);
    categoryService.create(tenant.getId(), CategoryDomain.SHARED_RESOURCE, name, otherParentId);
    assertTrue(categoryService.names(tenant.getId(), CategoryDomain.SHARED_RESOURCE).stream()
        .filter(n -> n.equalsIgnoreCase(name)).count() >= 2);
  }

  @Test
  void rejectsDeleteWhenChildExists() {
    Tenant tenant = newTenant("Del");
    String parentName = "부모-" + System.nanoTime();
    categoryService.create(tenant.getId(), CategoryDomain.SHARED_RESOURCE, parentName, null);
    UUID parentId = idOf(tenant.getId(), CategoryDomain.SHARED_RESOURCE, parentName);
    categoryService.create(tenant.getId(), CategoryDomain.SHARED_RESOURCE, "자식-" + System.nanoTime(), parentId);

    assertThrows(CategoryInUseException.class, () -> categoryService.delete(tenant.getId(), parentId));
  }

  @Test
  void crossTenantCannotReadOrModifyOrDeleteAnotherTenantsCategory() {
    Tenant tenantA = newTenant("IsoA");
    Tenant tenantB = newTenant("IsoB");

    String parentName = "격리부모-" + System.nanoTime();
    categoryService.create(tenantA.getId(), CategoryDomain.SHARED_RESOURCE, parentName, null);
    UUID parentId = idOf(tenantA.getId(), CategoryDomain.SHARED_RESOURCE, parentName);
    String childName = "격리자식-" + System.nanoTime();
    categoryService.create(tenantA.getId(), CategoryDomain.SHARED_RESOURCE, childName, parentId);
    UUID childId = idOf(tenantA.getId(), CategoryDomain.SHARED_RESOURCE, childName);

    UUID tenantBId = tenantB.getId();
    assertThrows(CategoryNotFoundException.class, () -> categoryService.rename(tenantBId, parentId, "탈취"));
    assertThrows(CategoryNotFoundException.class, () -> categoryService.delete(tenantBId, parentId));
    assertThrows(CategoryNotFoundException.class, () -> categoryService.rename(tenantBId, childId, "탈취자식"));
    assertThrows(CategoryNotFoundException.class, () -> categoryService.delete(tenantBId, childId));

    // B에서의 시도가 A쪽 데이터에 영향을 주지 않았는지: 이름 그대로 남아있고, 자식이 있어 여전히 삭제가 막힌다.
    assertTrue(categoryService.names(tenantA.getId(), CategoryDomain.SHARED_RESOURCE).contains(parentName));
    assertTrue(categoryService.names(tenantA.getId(), CategoryDomain.SHARED_RESOURCE).contains(childName));
    assertThrows(CategoryInUseException.class, () -> categoryService.delete(tenantA.getId(), parentId));

    // B 자신의 동일 도메인 목록에는 A의 카테고리가 전혀 노출되지 않는다.
    assertFalse(categoryService.names(tenantB.getId(), CategoryDomain.SHARED_RESOURCE).contains(parentName));
    assertFalse(categoryService.names(tenantB.getId(), CategoryDomain.SHARED_RESOURCE).contains(childName));
  }

  @Test
  void rejectsCreatingBeyondMaxDepth() {
    Tenant tenant = newTenant("Depth");
    categoryService.tree(tenant.getId(), CategoryDomain.ASSET); // 시드 트리거(실물>사무기기>컴퓨터, depth 0/1/2)
    UUID computerId = idOf(tenant.getId(), CategoryDomain.ASSET, "컴퓨터");

    // "컴퓨터"는 이미 depth 2(0-기준)라 그 아래 자식을 더 만들면 depth 3이 되어 MAX_DEPTH(3)를 넘는다.
    assertThrows(IllegalArgumentException.class,
        () -> categoryService.create(
            tenant.getId(), CategoryDomain.ASSET, "초과-" + System.nanoTime(), computerId));
  }

  private Tenant newTenant(String label) {
    return tenantService.createTenant(
        new CreateTenantCommand(label + " " + System.nanoTime(), (label + System.nanoTime()).toUpperCase()));
  }

  private UUID idOf(UUID tenantId, CategoryDomain domain, String name) {
    return categoryService.tree(tenantId, domain).stream()
        .filter(n -> n.name().equals(name))
        .map(CategoryService.CategoryNode::id)
        .findFirst()
        .orElseThrow(() -> new AssertionError("카테고리를 찾지 못함: " + name));
  }

  private CategoryService.CategoryNode findByName(List<CategoryService.CategoryNode> nodes, String name) {
    return nodes.stream()
        .filter(n -> n.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("카테고리를 찾지 못함: " + name));
  }
}
