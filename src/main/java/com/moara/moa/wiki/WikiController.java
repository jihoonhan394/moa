package com.moara.moa.wiki;

import com.moara.moa.user.ManagedUserService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 위키 진입·탐색. 접근 가능한 공간의 진입점 목록(홈)과 전문 검색을 담당한다.
 *
 * <p>위키는 컨트롤러 5개로 나뉜다 — 진입({@code WikiController}), 공간·권한·서식
 * ({@link WikiSpaceController}), 문서 본체({@link WikiPageController}),
 * 첨부({@link WikiAttachmentController}), AI 비동기 엔드포인트({@link WikiAiController}).
 * 공통 인가 판단은 {@link WikiAccessGuard}에 있다.
 */
@Controller
public class WikiController {
  private final WikiSpaceService spaceService;
  private final WikiPageService pageService;
  private final WikiEngagementService engagementService;
  private final ManagedUserService userService;
  private final WikiAccessGuard guard;

  public WikiController(
      WikiSpaceService spaceService, WikiPageService pageService,
      WikiEngagementService engagementService, ManagedUserService userService,
      WikiAccessGuard guard) {
    this.spaceService = spaceService;
    this.pageService = pageService;
    this.engagementService = engagementService;
    this.userService = userService;
    this.guard = guard;
  }

  // ── 홈: 접근 가능한 공간의 진입점(루트) 목록 ──────────────────────────────
  @GetMapping("/wiki")
  public String home(Model model) {
    UUID tenantId = guard.tenantId();
    List<WikiSpace> accessible = guard.accessibleSpaces();
    Set<UUID> accessibleIds = new HashSet<>();
    accessible.forEach(s -> accessibleIds.add(s.getId()));
    // 루트 = 부모가 없거나 부모가 접근 불가한 접근 가능 공간(고아 서브폴더도 진입점으로 노출).
    List<WikiSpace> roots = accessible.stream()
        .filter(s -> s.getParentId() == null || !accessibleIds.contains(s.getParentId()))
        .toList();
    model.addAttribute("spaces", roots);
    model.addAttribute("canCreateTop", guard.tenantAdmin());
    if (guard.tenantAdmin()) {
      model.addAttribute("tenantUsers", userService.findByTenant(tenantId));
    }
    if (!model.containsAttribute("spaceForm")) {
      model.addAttribute("spaceForm", new WikiSpaceForm(null, null));
    }
    model.addAttribute("favorites", favoriteRows(tenantId));
    model.addAttribute("page", "wiki");
    return "wiki/home";
  }

  /** 즐겨찾기 목록(접근 가능한 것만). 삭제된 페이지가 섞여 있어도 화면이 깨지지 않게 건너뛴다. */
  private List<Map<String, Object>> favoriteRows(UUID tenantId) {
    Set<UUID> favoriteIds = engagementService.favoritePageIds(tenantId, guard.userId());
    List<Map<String, Object>> favorites = new ArrayList<>();
    if (favoriteIds.isEmpty()) {
      return favorites;
    }
    Map<UUID, String> spaceNames = spaceNames(tenantId);
    for (UUID favPageId : favoriteIds) {
      try {
        WikiPage favPage = pageService.findById(tenantId, favPageId);
        if (favPage.getSpaceId() != null && guard.canView(favPage.getSpaceId())) {
          Map<String, Object> row = new HashMap<>();
          row.put("pageId", favPage.getId());
          row.put("title", favPage.getTitle());
          row.put("spaceName", spaceNames.getOrDefault(favPage.getSpaceId(), "(공간)"));
          favorites.add(row);
        }
      } catch (RuntimeException ignored) {
        // 삭제된 페이지 등은 건너뜀
      }
    }
    return favorites;
  }

  // ── 검색(접근 가능한 공간의 문서만) ─────────────────────────────────────
  @GetMapping("/wiki/search")
  public String search(@RequestParam(required = false) String q, Model model) {
    UUID tenantId = guard.tenantId();
    List<Map<String, Object>> results = new ArrayList<>();
    if (q != null && !q.isBlank()) {
      Map<UUID, String> spaceNames = spaceNames(tenantId);
      for (WikiPage wikiPage : pageService.search(tenantId, q)) {
        if (wikiPage.getSpaceId() != null && guard.canView(wikiPage.getSpaceId())) {
          Map<String, Object> row = new HashMap<>();
          row.put("pageId", wikiPage.getId());
          row.put("title", wikiPage.getTitle());
          row.put("spaceName", spaceNames.getOrDefault(wikiPage.getSpaceId(), "(공간)"));
          row.put("snippet", snippet(wikiPage.getContent()));
          results.add(row);
        }
      }
    }
    model.addAttribute("q", q);
    model.addAttribute("results", results);
    model.addAttribute("page", "wiki");
    return "wiki/search";
  }

  private Map<UUID, String> spaceNames(UUID tenantId) {
    Map<UUID, String> names = new HashMap<>();
    spaceService.findAll(tenantId).forEach(s -> names.put(s.getId(), s.getName()));
    return names;
  }

  private String snippet(String content) {
    if (content == null) {
      return "";
    }
    String flat = content.replaceAll("\s+", " ").trim();
    return flat.length() > 160 ? flat.substring(0, 160) + "…" : flat;
  }
}
