package com.moara.moa.mobile;

/**
 * 알림에 적힌 PC 경로를 모바일 화면 경로로 바꾼다.
 *
 * <h2>왜 필요한가</h2>
 *
 * {@code Notification.link} 는 경로 하나다. 알림을 만드는 쪽(배치·서비스)은 받는 사람이 PC 로
 * 볼지 폰으로 볼지 모르므로 <b>PC 경로(정식 경로)를 적는다</b>. 폰에서 그 링크를 누르면 앱
 * 웹뷰에 PC 화면이 떠서 글자가 깨알같아진다 — 장애 알림을 받고 연락처를 찾는 상황에서 가장
 * 쓸모없는 결과다.
 *
 * <p>그래서 <b>모바일 화면이 읽을 때 바꾼다.</b> 알림을 만드는 쪽에 "모바일용 링크"를 따로
 * 들게 하지 않는 이유는, 알림 종류가 늘 때마다 두 경로를 다 기억해야 하고 한쪽을 빠뜨려도
 * 아무도 모르기 때문이다. 변환 규칙이 한 곳에 있으면 대응이 없는 경로는 그냥 PC 화면으로
 * 떨어진다(지금까지의 동작과 같다).
 */
final class MobileLinks {

  private MobileLinks() {}

  /**
   * @param link 알림에 저장된 경로(null 가능)
   * @return 모바일 대응 화면이 있으면 그 경로, 없으면 받은 값 그대로
   */
  static String toMobile(String link) {
    if (link == null || link.isBlank() || link.startsWith("/m/")) {
      return link;
    }
    // 솔루션 상세: 헬스 체크 알림이 이 경로를 쓴다. 모바일에 같은 화면이 있다.
    if (link.startsWith("/solutions/")) {
      String rest = link.substring("/solutions/".length());
      // 하위 경로(/edit 등)는 모바일에 없다 — 그건 PC 화면으로 보낸다.
      return rest.isEmpty() || rest.contains("/") ? link : "/m/solutions/" + rest;
    }
    if (link.equals("/solutions")) {
      return "/m/solutions";
    }
    if (link.equals("/notices")) {
      return "/m/notices";
    }
    return link;
  }
}
