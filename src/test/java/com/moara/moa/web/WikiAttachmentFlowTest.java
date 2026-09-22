package com.moara.moa.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import com.moara.moa.wiki.WikiAccessLevel;
import com.moara.moa.wiki.WikiAttachment;
import com.moara.moa.wiki.WikiAttachmentRepository;
import com.moara.moa.wiki.WikiPage;
import com.moara.moa.wiki.WikiPageForm;
import com.moara.moa.wiki.WikiPageService;
import com.moara.moa.wiki.WikiSpace;
import com.moara.moa.wiki.WikiSpaceForm;
import com.moara.moa.wiki.WikiSpaceService;
import com.moara.moa.wiki.WikiSubjectType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 위키 첨부: 편집권자 업로드→목록→다운로드→삭제 흐름과 열람전용 사용자 업로드 차단(fail-closed). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WikiAttachmentFlowTest {
  private static final UUID MOA = Tenant.DEFAULT_TENANT_ID;

  @Autowired private MockMvc mockMvc;
  @Autowired private WikiSpaceService spaceService;
  @Autowired private WikiPageService pageService;
  @Autowired private ManagedUserService userService;
  @Autowired private WikiAttachmentRepository attachmentRepository;

  @Test
  void uploadListDownloadDelete() throws Exception {
    WikiSpace space = space("첨부공간");
    ManagedUser u = user();
    spaceService.grant(MOA, space.getId(), WikiSubjectType.USER, u.getId(), WikiAccessLevel.EDIT);
    WikiPage page = pageService.create(MOA, space.getId(), u.getId(), new WikiPageForm("첨부문서", "본문"));

    MockMultipartFile file = new MockMultipartFile(
        "file", "note.txt", "text/plain", "hello-attach".getBytes());
    mockMvc.perform(multipart("/wiki/" + page.getId() + "/attachments").file(file)
            .with(authentication(auth(u))).with(csrf()))
        .andExpect(status().is3xxRedirection());

    List<WikiAttachment> saved = attachmentRepository.findByTenantIdAndPageIdOrderByCreatedAtDesc(MOA, page.getId());
    assertThat(saved).hasSize(1);
    assertThat(saved.get(0).getFilename()).isEqualTo("note.txt");

    // 문서 보기에 첨부 노출
    mockMvc.perform(get("/wiki/" + page.getId()).with(authentication(auth(u))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("note.txt")));

    // 다운로드: 저장 내용 그대로
    mockMvc.perform(get("/wiki/attachments/" + saved.get(0).getId()).with(authentication(auth(u))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("hello-attach")));

    // 삭제 후 목록 비어야
    mockMvc.perform(post("/wiki/attachments/" + saved.get(0).getId() + "/delete")
            .with(authentication(auth(u))).with(csrf()))
        .andExpect(status().is3xxRedirection());
    assertThat(attachmentRepository.findByTenantIdAndPageIdOrderByCreatedAtDesc(MOA, page.getId())).isEmpty();
  }

  @Test
  void viewerWithoutEditCannotUpload() throws Exception {
    WikiSpace space = space("열람전용첨부");
    ManagedUser u = user();
    spaceService.grant(MOA, space.getId(), WikiSubjectType.USER, u.getId(), WikiAccessLevel.VIEW);
    WikiPage page = pageService.create(MOA, space.getId(), u.getId(), new WikiPageForm("문서", "본문"));

    MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "x".getBytes());
    mockMvc.perform(multipart("/wiki/" + page.getId() + "/attachments").file(file)
            .with(authentication(auth(u))).with(csrf()))
        .andExpect(status().isForbidden());
  }

  private WikiSpace space(String name) {
    return spaceService.create(MOA, null, new WikiSpaceForm(name + "-" + System.nanoTime(), null));
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
