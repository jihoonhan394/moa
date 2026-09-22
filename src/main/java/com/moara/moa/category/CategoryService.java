package com.moara.moa.category;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리형 카테고리(자산 관리자 전용). 자산(ASSET)은 유형(실물/SW) 아래 다단계 트리, 공유자산은 평면.
 * 목록이 비면 도메인별 기본값을 lazy 시드해 등록 폼 셀렉트가 항상 후보를 갖게 한다.
 */
@Service
@Transactional(readOnly = true)
public class CategoryService {
  /** 자산 카테고리 기본 트리: 유형 루트(실물/SW) → 하위. 유형 라벨은 InventoryItemType과 일치시킨다. */
  private static final Map<String, List<SeedNode>> ASSET_SEED = Map.of(
      "실물", List.of(
          new SeedNode("사무기기", List.of("컴퓨터", "모니터", "프린터")),
          new SeedNode("가구", List.of()),
          new SeedNode("모바일기기", List.of())),
      "SW", List.of(
          new SeedNode("디자인", List.of("포토샵", "일러스트")),
          new SeedNode("개발도구", List.of()),
          new SeedNode("오피스", List.of())));

  private static final List<String> SHARED_SEED = List.of("차량", "회의실", "좌석", "기타");

  /** 서버 카테고리 기본 트리(유형 없음): 망 구역 → 서버 역할. */
  private static final List<SeedNode> SERVER_SEED = List.of(
      new SeedNode("내부망 서버", List.of("웹서버", "DB서버", "앱서버", "파일서버")),
      new SeedNode("DMZ", List.of("웹서버", "프록시")),
      new SeedNode("클라우드", List.of("인스턴스")));

  /** 솔루션 카테고리 기본 트리(유형 없음): 업무영역 → 구성요소. */
  private static final List<SeedNode> SOLUTION_SEED = List.of(
      new SeedNode("그룹웨어", List.of("WAS", "DB", "메일")),
      new SeedNode("백업", List.of()),
      new SeedNode("모니터링", List.of()),
      new SeedNode("보안", List.of()));

  public static final String PATH_SEP = " / ";
  public static final int MAX_DEPTH = 3;

  private final CategoryRepository repository;

  public CategoryService(CategoryRepository repository) {
    this.repository = repository;
  }

  // ── 공유자산(평면) ────────────────────────────────────────────
  @Transactional
  public List<Category> list(UUID tenantId, CategoryDomain domain) {
    ensureDefaults(tenantId, domain);
    return repository.findByTenantIdAndDomainOrderBySortOrderAscNameAsc(tenantId, domain);
  }

  @Transactional
  public List<String> names(UUID tenantId, CategoryDomain domain) {
    return list(tenantId, domain).stream().map(Category::getName).toList();
  }

  // ── 자산(트리) ────────────────────────────────────────────────
  /** 도메인 카테고리를 DFS 순서로 평탄화(경로·깊이 포함). 등록 폼 셀렉트와 관리 화면 공용. */
  @Transactional
  public List<CategoryNode> tree(UUID tenantId, CategoryDomain domain) {
    ensureDefaults(tenantId, domain);
    List<Category> all = repository.findByTenantIdAndDomainOrderBySortOrderAscNameAsc(tenantId, domain);
    Map<UUID, List<Category>> byParent = all.stream()
        .collect(Collectors.groupingBy(c -> c.getParentId() == null ? ROOT : c.getParentId()));
    List<CategoryNode> out = new ArrayList<>();
    appendChildren(byParent, ROOT, "", 0, out);
    return out;
  }

  public List<CategoryNode> assetTree(UUID tenantId) {
    return tree(tenantId, CategoryDomain.ASSET);
  }

  private void appendChildren(
      Map<UUID, List<Category>> byParent, UUID parent, String parentPath, int depth,
      List<CategoryNode> out) {
    for (Category c : byParent.getOrDefault(parent, List.of())) {
      String path = parentPath.isEmpty() ? c.getName() : parentPath + PATH_SEP + c.getName();
      boolean hasChildren = byParent.containsKey(c.getId());
      out.add(new CategoryNode(c.getId(), c.getName(), c.getParentId(), c.getItemType(), depth, path,
          hasChildren));
      appendChildren(byParent, c.getId(), path, depth + 1, out);
    }
  }

  // ── CRUD ─────────────────────────────────────────────────────
  /** 하위 추가. parentId null이면 도메인 루트에. 이름은 형제 범위 내 대소문자 무시 유일. */
  @Transactional
  public void create(UUID tenantId, CategoryDomain domain, String rawName, UUID parentId) {
    String name = normalize(rawName);
    Category parent = null;
    if (parentId != null) {
      parent = repository.findByIdAndTenantId(parentId, tenantId)
          .orElseThrow(() -> new CategoryNotFoundException(parentId));
      if (depthOf(tenantId, parent) >= MAX_DEPTH - 1) {
        throw new IllegalArgumentException("카테고리는 최대 " + MAX_DEPTH + "단계까지만 만들 수 있습니다.");
      }
    }
    if (repository.existsByTenantIdAndDomainAndParentIdAndNameIgnoreCase(tenantId, domain, parentId, name)) {
      throw new DuplicateCategoryException(name);
    }
    String itemType = parent != null ? parent.getItemType() : null;
    int order = repository.findByTenantIdAndDomainOrderBySortOrderAscNameAsc(tenantId, domain).size();
    repository.save(new Category(
        UUID.randomUUID(), tenantId, domain, name, order, parentId, itemType, OffsetDateTime.now()));
  }

  @Transactional
  public void rename(UUID tenantId, UUID id, String rawName) {
    String name = normalize(rawName);
    Category category = repository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new CategoryNotFoundException(id));
    if (repository.existsByTenantIdAndDomainAndParentIdAndNameIgnoreCaseAndIdNot(
        tenantId, category.getDomain(), category.getParentId(), name, id)) {
      throw new DuplicateCategoryException(name);
    }
    category.rename(name);
    repository.save(category);
  }

  @Transactional
  public void delete(UUID tenantId, UUID id) {
    Category category = repository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new CategoryNotFoundException(id));
    if (repository.existsByParentId(id)) {
      throw new CategoryInUseException("하위 카테고리가 있어 삭제할 수 없습니다. 먼저 하위를 삭제하세요.");
    }
    repository.delete(category);
  }

  // ── 시드 ─────────────────────────────────────────────────────
  private void ensureDefaults(UUID tenantId, CategoryDomain domain) {
    if (repository.countByTenantIdAndDomain(tenantId, domain) > 0) {
      return;
    }
    OffsetDateTime now = OffsetDateTime.now();
    if (domain == CategoryDomain.SHARED_RESOURCE) {
      int order = 0;
      for (String name : SHARED_SEED) {
        repository.save(new Category(UUID.randomUUID(), tenantId, domain, name, order++, null, null, now));
      }
      return;
    }
    if (domain == CategoryDomain.SERVER || domain == CategoryDomain.SOLUTION) {
      List<SeedNode> seed = domain == CategoryDomain.SERVER ? SERVER_SEED : SOLUTION_SEED;
      int rootOrder = 0;
      for (SeedNode root : seed) {
        Category rootCat = new Category(
            UUID.randomUUID(), tenantId, domain, root.name(), rootOrder++, null, null, now);
        repository.save(rootCat);
        int childOrder = 0;
        for (String child : root.children()) {
          repository.save(new Category(
              UUID.randomUUID(), tenantId, domain, child, childOrder++, rootCat.getId(), null, now));
        }
      }
      return;
    }
    // ASSET: 유형 루트(실물/SW) → 하위. 루트/하위 모두 itemType은 유형 라벨을 캐리.
    int rootOrder = 0;
    for (String typeLabel : List.of("실물", "SW")) {
      Category root = new Category(
          UUID.randomUUID(), tenantId, domain, typeLabel, rootOrder++, null, typeLabel, now);
      repository.save(root);
      int childOrder = 0;
      for (SeedNode child : ASSET_SEED.getOrDefault(typeLabel, List.of())) {
        Category childCat = new Category(
            UUID.randomUUID(), tenantId, domain, child.name(), childOrder++, root.getId(), typeLabel, now);
        repository.save(childCat);
        int leafOrder = 0;
        for (String leaf : child.children()) {
          repository.save(new Category(
              UUID.randomUUID(), tenantId, domain, leaf, leafOrder++, childCat.getId(), typeLabel, now));
        }
      }
    }
  }

  /** 노드의 0-기준 깊이(루트=0). 상위 체인을 따라 계산. */
  private int depthOf(UUID tenantId, Category node) {
    int depth = 0;
    UUID pid = node.getParentId();
    while (pid != null && depth < MAX_DEPTH + 2) {
      Category parent = repository.findByIdAndTenantId(pid, tenantId).orElse(null);
      if (parent == null) {
        break;
      }
      depth++;
      pid = parent.getParentId();
    }
    return depth;
  }

  private static String normalize(String raw) {
    String name = raw == null ? "" : raw.trim();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("카테고리 이름을 입력하세요.");
    }
    if (name.contains(PATH_SEP.trim())) {
      throw new IllegalArgumentException("카테고리 이름에 '/'는 쓸 수 없습니다.");
    }
    return name.length() > 100 ? name.substring(0, 100) : name;
  }

  private static final UUID ROOT = new UUID(0L, 0L);

  private record SeedNode(String name, List<String> children) {}

  /** 트리 평탄화 노드(경로·깊이·하위존재). */
  public record CategoryNode(
      UUID id, String name, UUID parentId, String itemType, int depth, String path, boolean hasChildren) {}
}
