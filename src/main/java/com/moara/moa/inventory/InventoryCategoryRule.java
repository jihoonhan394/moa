package com.moara.moa.inventory;

import com.moara.moa.category.CategoryService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * "이 분류가 쓸 수 있는 것인가, 그러면 경로와 유형은 무엇인가"를 정하는 한 곳.
 *
 * <h2>왜 모았나</h2>
 *
 * 자산을 만드는 길이 넷이다 — PC 등록, PC 수정, CSV 임포트, 모바일 QR 등록. 넷이 각자
 * 검사하다 보니 실제로 어긋나 있었다.
 *
 * <ul>
 *   <li>PC 등록·수정은 <b>최상위만</b>(실물/SW) 봤다. 그래서 {@code 실물 / 없는분류 / 더없음}
 *       같은 값이 그대로 저장됐고, <b>그 자산은 어느 분류 필터에도 안 걸렸다.</b>
 *       화면을 열어 둔 사이 관리자가 분류를 지운 경우가 실제로 그렇다.
 *   <li>CSV 임포트는 <b>아무 검사도 없었다.</b> 대량이라 더 나쁘다 — 한 번에 수백 건이 들어간다.
 *   <li>모바일만 경로 실재 여부를 봤다(먼저 고쳤다).
 * </ul>
 *
 * <h2>짧은 이름도 받는다</h2>
 *
 * 화면은 셀렉트에서 전체 경로({@code 실물 / 사무기기 / 모니터})를 보내지만, <b>CSV 는 사람이
 * 손으로 쓴다</b> — 지금까지 쓰인 파일들은 {@code 모니터} 처럼 짧은 이름을 넣었다. 전체 경로만
 * 받기로 하면 기존 CSV 가 전부 거부된다. 그래서 이름이 트리에서 <b>하나로 정해지면</b> 그
 * 경로로 바꿔 준다. 같은 이름이 두 곳에 있으면 어느 쪽인지 우리가 고를 수 없으므로
 * 후보를 보여 주고 거부한다.
 *
 * <h2>유형은 분류에서 나온다 — 따로 받지 않는다</h2>
 *
 * 화면은 전부 분류 경로의 최상위로 유형을 파생한다. CSV 는 유형 열을 따로 받는데, 그러면
 * {@code 유형=SW, 분류=실물 / 사무기기 / 모니터} 처럼 <b>서로 모순된 행</b>이 들어갈 수 있다.
 * 그런 자산은 유형별 집계와 분류별 집계가 영원히 어긋난다.
 */
@Component
public class InventoryCategoryRule {

  /** 비어 있거나, 최상위가 실물/SW 가 아니다. */
  public static final String MISSING = "분류를 선택하세요(실물/SW 아래 경로).";

  /** 그런 분류가 이 기관에 없다. */
  public static final String UNKNOWN = "없는 분류입니다. 다시 선택하세요.";

  private final CategoryService categoryService;

  public InventoryCategoryRule(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  /**
   * 판정 결과. {@code error} 가 null 이면 통과이고, 그때 {@code path} 와 {@code type} 을 쓴다.
   *
   * <p><b>{@code path} 를 함께 돌려주는 이유</b>: 짧은 이름을 받았을 때 저장할 값은 입력이
   * 아니라 <b>해석된 전체 경로</b>다. 입력을 그대로 저장하면 분류 필터에 안 걸리는,
   * 고치려던 바로 그 상태가 된다.
   *
   * <p>예외로 던지지 않는 이유: 호출부마다 오류를 흘리는 경로가 다르다(화면은
   * {@code BindingResult}, 임포트는 행 오류 목록). 던지면 각자 잡아서 다시 포장해야 한다.
   */
  public record Resolution(InventoryItemType type, String path, String error) {
    public boolean ok() {
      return error == null;
    }
  }

  /** 한 건 판정. 화면 등록·수정이 쓴다. */
  public Resolution resolve(UUID tenantId, String input) {
    return resolve(input, index(tenantId));
  }

  /**
   * 이 기관의 자산 분류 색인.
   *
   * <p>여러 건을 볼 때는 이걸 한 번 만들어 돌려 쓴다. 행마다 {@link #resolve(UUID, String)}
   * 를 부르면 <b>CSV 1000행이면 분류 트리를 1000번 만든다</b>.
   */
  public CategoryIndex index(UUID tenantId) {
    Map<String, List<String>> byName = new LinkedHashMap<>();
    List<String> paths = new ArrayList<>();
    for (CategoryService.CategoryNode node : categoryService.assetTree(tenantId)) {
      paths.add(node.path());
      byName.computeIfAbsent(node.name(), key -> new ArrayList<>()).add(node.path());
    }
    return new CategoryIndex(Set.copyOf(paths), byName);
  }

  /** 미리 만들어 둔 색인으로 판정한다. */
  public Resolution resolve(String input, CategoryIndex index) {
    String trimmed = input == null ? "" : input.trim();
    if (trimmed.isEmpty()) {
      return reject(MISSING);
    }

    // 경로를 먼저 정하고, 유형은 그 경로에서 뽑는다. 순서를 뒤집으면 짧은 이름("모니터")이
    // 최상위 검사에서 걸려 "없는 분류"가 아니라 "분류를 선택하세요"로 나간다 — 틀린 안내다.
    String path = index.pathOf(trimmed);
    if (path == null) {
      List<String> candidates = index.byName().getOrDefault(trimmed, List.of());
      if (candidates.size() > 1) {
        return reject("같은 이름의 분류가 여럿입니다 — 전체 경로로 적어 주세요: "
            + String.join(" / 또는 / ", candidates));
      }
      return reject(UNKNOWN);
    }

    InventoryItemType type = InventoryItemType.fromCategoryPath(path);
    if (type == null) {
      // 최상위가 실물/SW 가 아닌 분류를 누가 만들었다. 유형을 정할 수 없다.
      return reject(MISSING);
    }
    return new Resolution(type, path, null);
  }

  private static Resolution reject(String error) {
    return new Resolution(null, null, error);
  }

  /**
   * 분류 트리를 조회용으로 펼쳐 둔 것. 전체 경로 집합과 이름→경로 목록.
   *
   * @param paths 전체 경로 집합(중간 노드도 포함 — 굵게 분류하는 것도 유효하다)
   * @param byName 마지막 한 칸 이름 → 그 이름을 가진 경로들
   */
  public record CategoryIndex(Set<String> paths, Map<String, List<String>> byName) {

    /** 전체 경로면 그대로, 하나로 정해지는 짧은 이름이면 그 경로. 아니면 null. */
    String pathOf(String input) {
      if (paths.contains(input)) {
        return input;
      }
      List<String> candidates = byName.getOrDefault(input, List.of());
      return candidates.size() == 1 ? candidates.get(0) : null;
    }
  }
}
