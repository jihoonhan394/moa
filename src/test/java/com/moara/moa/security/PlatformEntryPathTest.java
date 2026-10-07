package com.moara.moa.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 플랫폼 은닉 진입 경로의 허용 범위.
 *
 * <p>조금만 틀리면 <b>아무 안내 없이</b> {@code /enter}(기관 코드 화면)로 떨어진다. 그러면
 * 운영자는 "안 들어가진다"로 받아들이고, 은닉 경로라 물어볼 데도 없다. 어디까지 받아 주고
 * 어디부터 안 받는지를 테스트가 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformEntryPathTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("정확한 경로는 로그인으로 보낸다")
  void 정확한_경로는_통과한다() throws Exception {
    mockMvc.perform(get("/platform")).andExpect(redirectedUrl("/login"));
  }

  @Test
  @DisplayName("⚠️ 끝 슬래시도 같은 경로로 본다 — 사람이 아니라 브라우저가 붙인다")
  void 끝_슬래시도_통과한다() throws Exception {
    mockMvc.perform(get("/platform/")).andExpect(redirectedUrl("/login"));
  }

  @Test
  @DisplayName("쿼리스트링이 붙어도 통과한다")
  void 쿼리는_상관없다() throws Exception {
    mockMvc.perform(get("/platform").param("x", "1")).andExpect(redirectedUrl("/login"));
  }

  @Test
  @DisplayName("대소문자는 가리지 않는다 — 타이포이고, 은닉 경로는 적힌 그대로만 맞는 편이 낫다")
  void 대문자는_통과하지_않는다() throws Exception {
    mockMvc.perform(get("/Platform"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/enter"));
  }

  @Test
  @DisplayName("하위 경로는 진입이 아니다")
  void 하위_경로는_진입이_아니다() throws Exception {
    mockMvc.perform(get("/platform/extra"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/enter"));
  }
}
