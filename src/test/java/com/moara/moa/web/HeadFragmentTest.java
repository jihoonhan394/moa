package com.moara.moa.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 모든 화면의 {@code <head>}가 프래그먼트 하나에서 나온다.
 *
 * <p>화면마다 다른 것은 제목뿐이라 프래그먼트 인자로 넘기는데, 그 표현식은 템플릿 본문이 아니라
 * 프래그먼트 호출 자리에 들어가므로 컴파일로는 검증되지 않는다 — 틀리면 그 화면을 열 때
 * 비로소 터진다. 그래서 제목 표현식의 모양별로 한 화면씩 실제로 렌더해 본다.
 *
 * <p>나머지 모양(엔티티 이름을 이어 붙이는 {@code 'MOA - ' + ${x.name}})은 위키 화면 테스트가
 * 이미 렌더한다(WikiFlowTest: 공간·본문·편집기 — CSRF 메타가 붙는 변형까지).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HeadFragmentTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ManagedUserService userService;

  /** 조건에 따라 제목이 갈리는 모양: {@code ${userId == null ? ... : ...}}. */
  @Test
  void rendersConditionalTitle() throws Exception {
    mockMvc.perform(get("/users/new").with(authentication(auth(user(), "ROLE_TENANT_ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("<title>MOA - 사용자 추가</title>")))
        .andExpect(content().string(Matchers.containsString("href=\"/css/style.css\"")));
  }

  /** 모델 값을 이어 붙이는 모양: {@code 'MOA - ' + ${domainLabel} + ' 카테고리'}. */
  @Test
  void rendersConcatenatedTitle() throws Exception {
    mockMvc.perform(
            get("/inventory/categories").with(authentication(auth(user(), "ROLE_ASSET_MANAGER"))))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("카테고리</title>")));
  }

  /** 고정 제목 모양. 메타 태그까지 프래그먼트에서 오는지 함께 본다. */
  @Test
  void rendersLiteralTitleWithMeta() throws Exception {
    mockMvc.perform(get("/servers/new").with(authentication(auth(user(), "ROLE_INFRA_MANAGER"))))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("<meta charset=\"UTF-8\">")))
        .andExpect(content().string(Matchers.containsString(
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")));
  }

  /**
   * head에 있던 스크립트를 body 끝으로 옮겼다. 로그인 화면의 제품 미리보기를 그 스크립트가
   * 그리므로, 옮기다 빠뜨리면 로그인 화면이 빈 껍데기가 된다.
   */
  @Test
  void loginKeepsItsScript() throws Exception {
    // 로그인 화면은 기관을 고른 뒤에야 나온다(2단계 진입). 세션에 기관을 담아 들어간다.
    MockHttpSession session = new MockHttpSession();
    mockMvc.perform(post("/enter")
        .param("code", Tenant.DEFAULT_TENANT_CODE).session(session).with(csrf()));

    mockMvc.perform(get("/login").session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("/js/console-preview.js")))
        .andExpect(content().string(Matchers.containsString("href=\"/css/style.css\"")));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user, String role) {
    return new UsernamePasswordAuthenticationToken(
        new MoaUserDetails(user), "", List.of(new SimpleGrantedAuthority(role)));
  }

  private ManagedUser user() {
    String username = "head" + System.nanoTime();
    return userService.create(new UserForm(
        username, "화면 확인", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
