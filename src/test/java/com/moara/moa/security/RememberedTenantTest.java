package com.moara.moa.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * "이 기기에서 기관 코드를 기억" — 진입 1단계를 건너뛴다.
 *
 * <p>두레이식 2단계는 멀티테넌시에 맞지만 <b>한 회사 직원은 매일 같은 코드를 친다.</b>
 * 기억은 opt-in 이고, <b>빠져나갈 길</b>이 반드시 있어야 한다 — 그게 없으면 공용 PC 에서
 * 남의 기관 로그인 화면에 갇힌다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RememberedTenantTest {

  private static final String COOKIE = "MOA_TENANT";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantService tenantService;

  @Test
  @DisplayName("체크하면 쿠키가 생기고, 다음 방문에 1단계를 건너뛴다")
  void 기억하면_건너뛴다() throws Exception {
    Tenant tenant = newTenant();

    mockMvc.perform(post("/enter").param("code", tenant.getCode()).param("remember", "1")
            .with(org.springframework.security.test.web.servlet.request
                .SecurityMockMvcRequestPostProcessors.csrf()))
        .andExpect(redirectedUrl("/login"))
        .andExpect(cookie().value(COOKIE, tenant.getCode()))
        .andExpect(cookie().httpOnly(COOKIE, true));

    mockMvc.perform(get("/enter").cookie(new Cookie(COOKIE, tenant.getCode())))
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  @DisplayName("체크하지 않으면 기억하지 않는다 — 기본은 안 남기는 쪽이다")
  void 체크하지_않으면_기억하지_않는다() throws Exception {
    Tenant tenant = newTenant();

    mockMvc.perform(post("/enter").param("code", tenant.getCode())
            .with(org.springframework.security.test.web.servlet.request
                .SecurityMockMvcRequestPostProcessors.csrf()))
        .andExpect(redirectedUrl("/login"))
        .andExpect(cookie().maxAge(COOKIE, 0));

    mockMvc.perform(get("/enter"))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("기관 코드")));
  }

  @Test
  @DisplayName("⚠️ change=1 이면 기억을 지우고 화면을 보여 준다 — 공용 PC 의 빠져나갈 길")
  void 다른_기관으로_빠져나갈_수_있다() throws Exception {
    Tenant tenant = newTenant();

    mockMvc.perform(get("/enter").param("change", "1")
            .cookie(new Cookie(COOKIE, tenant.getCode())))
        .andExpect(status().isOk())
        .andExpect(cookie().maxAge(COOKIE, 0))
        .andExpect(content().string(Matchers.containsString("기관 코드")));
  }

  @Test
  @DisplayName("⚠️ 기억한 기관이 꺼지면 조용히 지우고 평소 화면을 보여 준다")
  void 꺼진_기관은_조용히_잊는다() throws Exception {
    Tenant tenant = newTenant();
    tenantService.disableTenant(tenant.getId());

    mockMvc.perform(get("/enter").cookie(new Cookie(COOKIE, tenant.getCode())))
        .andExpect(status().isOk())
        .andExpect(cookie().maxAge(COOKIE, 0))
        // 여기서 오류를 내면 사용자는 자기가 뭘 잘못했는지 알 수 없다. 기억은 편의 기능이다.
        .andExpect(content().string(Matchers.not(Matchers.containsString("비활성화된 기관"))));
  }

  @Test
  @DisplayName("⚠️ 쿠키를 손으로 고쳐도 통하지 않는다 — 서버가 다시 조회·검증한다")
  void 위조한_쿠키는_무시한다() throws Exception {
    for (String forged : new String[] {"NOPE" + System.nanoTime(), "'; drop table--", "../etc",
        "A".repeat(80)}) {
      mockMvc.perform(get("/enter").cookie(new Cookie(COOKIE, forged)))
          .andExpect(status().isOk())
          .andExpect(content().string(Matchers.containsString("기관 코드")));
    }
  }

  @Test
  @DisplayName("기억해 둔 기관으로 들어가면 그 기관이 세션에 선택된다")
  void 건너뛰어도_기관이_선택된다() throws Exception {
    Tenant tenant = newTenant();

    var session = mockMvc.perform(get("/enter").cookie(new Cookie(COOKIE, tenant.getCode())))
        .andExpect(redirectedUrl("/login"))
        .andReturn().getRequest().getSession(false);

    org.assertj.core.api.Assertions.assertThat(EntrySession.tenantId(session))
        .isEqualTo(tenant.getId());
    org.assertj.core.api.Assertions.assertThat(EntrySession.hasSelection(session)).isTrue();
  }

  private Tenant newTenant() {
    return tenantService.createTenant(
        new CreateTenantCommand("기억사", "RM" + System.nanoTime()));
  }
}
