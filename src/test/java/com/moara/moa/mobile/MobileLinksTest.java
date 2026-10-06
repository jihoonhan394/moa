package com.moara.moa.mobile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MobileLinksTest {

  @Test
  @DisplayName("솔루션 상세는 모바일 화면으로 보낸다 — 장애 알림이 이 경로를 쓴다")
  void 솔루션_상세는_모바일로() {
    UUID id = UUID.randomUUID();
    assertThat(MobileLinks.toMobile("/solutions/" + id)).isEqualTo("/m/solutions/" + id);
    assertThat(MobileLinks.toMobile("/solutions")).isEqualTo("/m/solutions");
  }

  @Test
  @DisplayName("⚠️ 모바일에 없는 하위 경로는 건드리지 않는다 — /m/solutions/{id}/edit 는 404 다")
  void 모바일에_없는_하위_경로는_그대로() {
    UUID id = UUID.randomUUID();
    assertThat(MobileLinks.toMobile("/solutions/" + id + "/edit"))
        .isEqualTo("/solutions/" + id + "/edit");
  }

  @Test
  @DisplayName("대응 화면이 없는 경로는 PC 화면으로 떨어진다(지금까지의 동작)")
  void 대응이_없으면_그대로() {
    assertThat(MobileLinks.toMobile("/expirations")).isEqualTo("/expirations");
    assertThat(MobileLinks.toMobile("/inventory")).isEqualTo("/inventory");
  }

  @Test
  @DisplayName("이미 모바일 경로면 두 번 바꾸지 않는다")
  void 이미_모바일이면_그대로() {
    assertThat(MobileLinks.toMobile("/m/solutions/abc")).isEqualTo("/m/solutions/abc");
  }

  @Test
  @DisplayName("링크가 없는 알림도 있다 — null·공백에서 터지지 않는다")
  void 링크가_없어도_된다() {
    assertThat(MobileLinks.toMobile(null)).isNull();
    assertThat(MobileLinks.toMobile("")).isEmpty();
    assertThat(MobileLinks.toMobile("/solutions/")).isEqualTo("/solutions/");
  }
}
