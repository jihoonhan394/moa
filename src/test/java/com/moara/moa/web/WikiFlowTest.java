package com.moara.moa.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupForm;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.group.AccessGroupStatus;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import com.moara.moa.wiki.WikiAccessLevel;
import com.moara.moa.wiki.WikiAccessService;
import com.moara.moa.wiki.WikiComment;
import com.moara.moa.wiki.WikiEngagementService;
import com.moara.moa.wiki.WikiPage;
import com.moara.moa.wiki.WikiPageForm;
import com.moara.moa.wiki.WikiPageRevision;
import com.moara.moa.wiki.WikiPageService;
import com.moara.moa.wiki.WikiSpace;
import com.moara.moa.wiki.WikiSpaceForm;
import com.moara.moa.wiki.WikiSpaceService;
import com.moara.moa.wiki.WikiSubjectType;
import com.moara.moa.wiki.WikiTemplate;
import com.moara.moa.wiki.WikiTemplateForm;
import com.moara.moa.wiki.WikiTemplateService;
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

/** 위키 v2: 공간 fail-closed·열람/편집/관리 게이팅·그룹 상속·기관관리자 백업·마크다운 새니타이즈. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WikiFlowTest {
  private static final UUID MOA = Tenant.DEFAULT_TENANT_ID;

  @Autowired private MockMvc mockMvc;
  @Autowired private WikiSpaceService spaceService;
  @Autowired private WikiPageService pageService;
  @Autowired private WikiAccessService accessService;
  @Autowired private ManagedUserService userService;
  @Autowired private AccessGroupService groupService;
  @Autowired private WikiTemplateService templateService;
  @Autowired private WikiEngagementService engagementService;

  @Test
  void labelAddDedupAndRemove() {
    WikiSpace space = space(null, "라벨공간");
    ManagedUser u = user();
    spaceService.grant(MOA, space.getId(), WikiSubjectType.USER, u.getId(), WikiAccessLevel.EDIT);
    WikiPage page = pageService.create(MOA, space.getId(), u.getId(), new WikiPageForm("문서", "내용"));

    engagementService.addLabel(MOA, page.getId(), "가이드");
    engagementService.addLabel(MOA, page.getId(), "가이드"); // 중복 무시
    assertEquals(1, engagementService.labels(MOA, page.getId()).size());
    engagementService.removeLabel(MOA, engagementService.labels(MOA, page.getId()).get(0).getId());
    assertTrue(engagementService.labels(MOA, page.getId()).isEmpty());
  }

  @Test
  void favoriteToggles() {
    WikiSpace space = space(null, "즐겨찾기공간");
    ManagedUser u = user();
    spaceService.grant(MOA, space.getId(), WikiSubjectType.USER, u.getId(), WikiAccessLevel.VIEW);
    WikiPage page = pageService.create(MOA, space.getId(), u.getId(), new WikiPageForm("문서", "내용"));

    assertTrue(engagementService.toggleFavorite(MOA, u.getId(), page.getId()));
    assertTrue(engagementService.isFavorite(MOA, u.getId(), page.getId()));
    assertFalse(engagementService.toggleFavorite(MOA, u.getId(), page.getId()));
    assertFalse(engagementService.isFavorite(MOA, u.getId(), page.getId()));
  }

  @Test
  void commentDeleteAuthzViewerCannotDeleteOthers() throws Exception {
    WikiSpace space = space(null, "댓글공간");
    ManagedUser author = user();
    ManagedUser other = user();
    spaceService.grant(MOA, space.getId(), WikiSubjectType.USER, author.getId(), WikiAccessLevel.VIEW);
    spaceService.grant(MOA, space.getId(), WikiSubjectType.USER, other.getId(), WikiAccessLevel.VIEW);
    WikiPage page = pageService.create(MOA, space.getId(), author.getId(), new WikiPageForm("문서", "내용"));
    WikiComment comment = engagementService.addComment(MOA, page.getId(), author.getId(), "의견");

    // 타인(열람자)은 남의 댓글 삭제 불가.
    mockMvc.perform(post("/wiki/" + page.getId() + "/comments/" + comment.getId() + "/delete")
            .with(authentication(auth(other))).with(csrf()))
        .andExpect(status().isForbidden());
    // 작성자는 삭제 가능.
    mockMvc.perform(post("/wiki/" + page.getId() + "/comments/" + comment.getId() + "/delete")
            .with(authentication(auth(author))).with(csrf()))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void managerCreatesTemplateEditorLoadsIt() throws Exception {
    WikiSpace space = space(null, "서식공간");
    ManagedUser mgr = user();
    spaceService.grant(MOA, space.getId(), WikiSubjectType.USER, mgr.getId(), WikiAccessLevel.MANAGE);

    mockMvc.perform(post("/wiki/spaces/" + space.getId() + "/templates").with(authentication(auth(mgr))).with(csrf())
            .param("name", "회의록").param("content", "# 회의록 서식본문"))
        .andExpect(status().is3xxRedirection());
    WikiTemplate template = templateService.findBySpace(MOA, space.getId()).get(0);

    // 서식 불러오기 → 폼에 본문 채워짐.
    mockMvc.perform(post("/wiki/spaces/" + space.getId() + "/pages/from-template")
            .with(authentication(auth(mgr))).with(csrf()).param("templateId", template.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("회의록 서식본문")));
  }

  @Test
  void searchReturnsOnlyAccessibleSpaces() throws Exception {
    String kw = "키워드" + System.nanoTime();
    WikiSpace visible = space(null, "보이는");
    WikiSpace hidden = space(null, "숨은");
    ManagedUser u = user();
    spaceService.grant(MOA, visible.getId(), WikiSubjectType.USER, u.getId(), WikiAccessLevel.VIEW);
    pageService.create(MOA, visible.getId(), u.getId(), new WikiPageForm("문서A", "본문 " + kw));
    pageService.create(MOA, hidden.getId(), u.getId(), new WikiPageForm("문서B", "본문 " + kw));

    mockMvc.perform(get("/wiki/search").param("q", kw).with(authentication(auth(u))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("문서A")))
        .andExpect(content().string(not(containsString("문서B"))));
  }

  @Test
  void editSnapshotsHistoryAndRestore() {
    WikiSpace space = space(null, "이력공간");
    ManagedUser u = user();
    spaceService.grant(MOA, space.getId(), WikiSubjectType.USER, u.getId(), WikiAccessLevel.EDIT);
    WikiPage page = pageService.create(MOA, space.getId(), u.getId(), new WikiPageForm("문서", "버전1내용"));
    pageService.update(MOA, page.getId(), u.getId(), new WikiPageForm("문서", "버전2내용"));

    List<WikiPageRevision> revs = pageService.revisions(MOA, page.getId());
    assertEquals(1, revs.size());
    assertEquals("버전1내용", revs.get(0).getContent());

    pageService.restore(MOA, page.getId(), revs.get(0).getId(), u.getId());
    assertEquals("버전1내용", pageService.findById(MOA, page.getId()).getContent());
    assertEquals(2, pageService.revisions(MOA, page.getId()).size(), "복원 시 현재본도 이력에 남음");
  }

  @Test
  void viewerCannotCreateTemplate() throws Exception {
    WikiSpace space = space(null, "서식차단");
    ManagedUser u = user();
    spaceService.grant(MOA, space.getId(), WikiSubjectType.USER, u.getId(), WikiAccessLevel.VIEW);
    mockMvc.perform(post("/wiki/spaces/" + space.getId() + "/templates").with(authentication(auth(u))).with(csrf())
            .param("name", "x").param("content", "y"))
        .andExpect(status().isForbidden());
  }

  @Test
  void failClosedThenViewEditManage() {
    WikiSpace space = space(null, "영업위키");
    UUID u = user().getId();
    assertFalse(accessService.canView(MOA, u, false, space.getId()), "권한 없으면 접근 불가");

    spaceService.grant(MOA, space.getId(), WikiSubjectType.USER, u, WikiAccessLevel.VIEW);
    assertTrue(accessService.canView(MOA, u, false, space.getId()));
    assertFalse(accessService.canEdit(MOA, u, false, space.getId()));

    spaceService.grant(MOA, space.getId(), WikiSubjectType.USER, u, WikiAccessLevel.EDIT);
    assertTrue(accessService.canEdit(MOA, u, false, space.getId()));
    assertFalse(accessService.canManage(MOA, u, false, space.getId()));

    spaceService.grant(MOA, space.getId(), WikiSubjectType.USER, u, WikiAccessLevel.MANAGE);
    assertTrue(accessService.canManage(MOA, u, false, space.getId()));
  }

  @Test
  void groupGrantInheritsToSubfolder() {
    WikiSpace parent = space(null, "부모");
    WikiSpace child = space(parent.getId(), "자식");
    AccessGroup group = groupService.create(MOA, new AccessGroupForm("g-" + System.nanoTime(), "설명", AccessGroupStatus.ACTIVE, null));
    ManagedUser member = user();
    groupService.addMember(MOA, group.getId(), member.getId());

    // 부모에 부서 VIEW → 자식도 상속.
    spaceService.grant(MOA, parent.getId(), WikiSubjectType.GROUP, group.getId(), WikiAccessLevel.VIEW);
    assertTrue(accessService.canView(MOA, member.getId(), false, child.getId()), "상위 폴더 그룹 권한이 하위로 상속");
    assertFalse(accessService.canView(MOA, user().getId(), false, child.getId()), "그룹 밖 사용자는 불가");
  }

  @Test
  void tenantAdminHasBackupManage() {
    WikiSpace space = space(null, "무권한공간");
    assertEquals(WikiAccessLevel.MANAGE,
        accessService.effectiveLevel(MOA, user().getId(), true, space.getId()));
  }

  @Test
  void webViewGatedByAccess() throws Exception {
    WikiSpace space = space(null, "게이팅");
    ManagedUser u = user();
    mockMvc.perform(get("/wiki/spaces/" + space.getId()).with(authentication(auth(u))))
        .andExpect(status().isForbidden());

    spaceService.grant(MOA, space.getId(), WikiSubjectType.USER, u.getId(), WikiAccessLevel.VIEW);
    mockMvc.perform(get("/wiki/spaces/" + space.getId()).with(authentication(auth(u))))
        .andExpect(status().isOk());
  }

  @Test
  void editorCreatesPageRenderedSanitized() throws Exception {
    WikiSpace space = space(null, "편집공간");
    ManagedUser u = user();
    spaceService.grant(MOA, space.getId(), WikiSubjectType.USER, u.getId(), WikiAccessLevel.EDIT);
    String title = "보안-" + System.nanoTime();

    mockMvc.perform(post("/wiki/spaces/" + space.getId() + "/pages").with(authentication(auth(u))).with(csrf())
            .param("title", title).param("content", "**굵게** <script>alert(1)</script>"))
        .andExpect(status().is3xxRedirection());

    WikiPage created = pageService.findBySpace(MOA, space.getId()).stream()
        .filter(p -> p.getTitle().equals(title)).findFirst().orElseThrow();
    mockMvc.perform(get("/wiki/" + created.getId()).with(authentication(auth(u))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<strong>굵게</strong>")))
        .andExpect(content().string(not(containsString("<script>"))));
  }

  @Test
  void viewerWithoutEditCannotOpenNewPage() throws Exception {
    WikiSpace space = space(null, "열람전용");
    ManagedUser u = user();
    spaceService.grant(MOA, space.getId(), WikiSubjectType.USER, u.getId(), WikiAccessLevel.VIEW);
    mockMvc.perform(get("/wiki/spaces/" + space.getId() + "/new").with(authentication(auth(u))))
        .andExpect(status().isForbidden());
  }

  private WikiSpace space(UUID parentId, String name) {
    return spaceService.create(MOA, parentId, new WikiSpaceForm(name + "-" + System.nanoTime(), null));
  }

  private ManagedUser user() {
    String username = "u" + System.nanoTime();
    return userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }
}
