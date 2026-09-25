package com.moara.moa.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moara.moa.tenant.Tenant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * AI 설정을 <b>확인할 수 있는지</b>.
 *
 * <p>저장만으로는 아무것도 증명되지 않는다. 키가 틀려도 화면은 "저장했습니다"라고 하고,
 * 문제는 한참 뒤 다른 기능에서 조용한 비활성으로 나타난다 — 그때는 원인을 설정으로
 * 되짚기 어렵다. 그래서 설정 화면에서 바로 왕복을 확인할 수 있어야 한다.
 *
 * <p>실제 제공자 호출은 테스트에서 하지 않는다(키도 없고, 외부에 의존하는 테스트는
 * 언젠가 남의 사정으로 빨개진다). 여기서 보는 것은 <b>호출 전에 코드가 내리는 판단</b>과
 * 사용자에게 돌려주는 말이다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AiDiagnosticsTest {
  @Autowired private AiService aiService;

  /** 설정이 없는 기관은 "키가 없다"고 말해야 한다 — 어디를 고칠지가 문장에 있어야 한다. */
  @Test
  void testTellsWhenNothingIsSaved() {
    AiService.TestResult result = aiService.test(UUID.randomUUID());

    assertFalse(result.ok());
    assertTrue(result.message().contains("키"), result.message());
    assertNull(result.reply());
  }

  /** 설정이 없으면 기능은 조용히 꺼져 있어야 한다(예외가 아니라 false). */
  @Test
  void unconfiguredTenantIsSimplyNotConfigured() {
    assertFalse(aiService.isConfigured(UUID.randomUUID()));
  }

  /** 설정 없이 호출하면 전용 예외 — 호출부가 "설정하세요"로 안내할 수 있어야 한다. */
  @Test
  void generateWithoutSettingRaisesNotConfigured() {
    UUID tenant = UUID.randomUUID();
    try {
      aiService.generate(tenant, "무엇이든");
      throw new AssertionError("설정이 없는데 호출이 통과했다");
    } catch (AiNotConfiguredException expected) {
      assertNotNull(expected);
    }
  }

  /** 기본 기관도 키가 없으면 마찬가지다(테스트 프로파일엔 키가 없다). */
  @Test
  void defaultTenantWithoutKeyIsReportedClearly() {
    AiService.TestResult result = aiService.test(Tenant.DEFAULT_TENANT_ID);

    assertFalse(result.ok(), "키가 없는데 연결됐다고 했다");
    assertNotNull(result.message());
  }

  /**
   * 제공자 기본값은 현행 세대여야 한다. 구세대 이름은 언젠가 제공자에서 사라져, 아무 설정도
   * 건드리지 않은 기관이 어느 날 404를 만난다.
   */
  @Test
  void providerDefaultsAreCurrent() {
    assertEquals("claude-sonnet-5", AiProvider.CLAUDE.getDefaultModel());
    for (AiProvider provider : AiProvider.values()) {
      assertNotNull(provider.getDefaultModel(), provider + " 기본 모델 없음");
      assertNotNull(provider.getDefaultBaseUrl(), provider + " 기본 주소 없음");
      assertTrue(provider.getDefaultBaseUrl().startsWith("https://"),
          provider + " 기본 주소가 평문 HTTP다 — API 키가 그대로 실린다");
    }
  }
}
