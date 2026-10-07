package com.moara.moa.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * "이 기기에서 기관 코드를 기억한다"를 쿠키로 둔다.
 *
 * <h2>왜 필요한가</h2>
 *
 * 두레이식 2단계(기관 코드 → 로그인)는 멀티테넌시에 맞는 선택이지만, <b>한 회사 직원은
 * 매일 같은 코드를 친다.</b> 하루에 몇 번씩 겪는 마찰이라 규모 문제보다 체감이 크다.
 *
 * <h2>왜 opt-in 인가</h2>
 *
 * 기관 코드는 비밀이 아니다 — 로그인 1단계 화면에서 사람이 직접 입력하는 값이다. 다만
 * <b>"이 기기를 어느 회사가 쓰는가"는 남는다.</b> 공용 PC·키오스크에서 그게 남으면 안 되는
 * 경우가 있으므로 켜는 것은 사용자가 고르고, 끌 길도 화면에 둔다.
 *
 * <h2>쿠키에 식별자를 담지 않는다</h2>
 *
 * 기관 <b>코드</b>만 담는다(UUID 아님). 쿠키는 클라이언트가 고칠 수 있으므로 어차피
 * 서버가 다시 조회·검증해야 하고, 그렇다면 사람이 읽을 수 있는 값이 디버깅에 낫다.
 * 쿠키가 가리키는 기관이 없거나 꺼져 있으면 <b>조용히 지우고</b> 평소 화면을 보여 준다 —
 * 거기서 오류를 내면 사용자는 자기가 뭘 잘못했는지 알 수 없다.
 */
@Component
public class RememberedTenant {

  /** 이름에 의미를 담지 않는다. 세션 쿠키와 구별되게만 둔다. */
  private static final String COOKIE = "MOA_TENANT";

  /** 1년. 더 짧으면 "기억한다"고 해 놓고 잊어버린다. */
  private static final int MAX_AGE_SECONDS = 60 * 60 * 24 * 365;

  /** 코드의 허용 문자는 {@code TenantForm} 의 패턴과 같다. 그보다 긴 값은 우리 것이 아니다. */
  private static final int MAX_LENGTH = 50;

  private final boolean secureCookie;

  public RememberedTenant(@Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
    this.secureCookie = secureCookie;
  }

  /** 기억된 기관 코드. 없거나 형식이 이상하면 비어 있다. */
  public Optional<String> read(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(cookie -> COOKIE.equals(cookie.getName()))
        .map(Cookie::getValue)
        .filter(value -> value != null && !value.isBlank())
        .filter(value -> value.length() <= MAX_LENGTH)
        // 쿠키는 클라이언트가 고칠 수 있다. 우리가 발급하는 형식이 아니면 쓰지 않는다.
        .filter(value -> value.matches("[A-Za-z0-9._-]+"))
        .findFirst();
  }

  public void remember(HttpServletResponse response, String code) {
    response.addCookie(cookie(code, MAX_AGE_SECONDS));
  }

  /** 기억을 끈다. 체크를 해제하고 들어왔을 때와 "다른 기관으로"를 누를 때 쓴다. */
  public void forget(HttpServletResponse response) {
    response.addCookie(cookie("", 0));
  }

  private Cookie cookie(String value, int maxAge) {
    Cookie cookie = new Cookie(COOKIE, value);
    cookie.setPath("/");
    cookie.setMaxAge(maxAge);
    // 이 값을 읽는 JS 가 없다. 읽을 수 있게 두면 서버 화면의 XSS 가 기관 코드를 가져간다.
    cookie.setHttpOnly(true);
    // HTTPS 뒤에서는 평문 연결에 실리지 않게 한다. 세션 쿠키와 같은 설정을 따른다.
    cookie.setSecure(secureCookie);
    // 외부 사이트에서 넘어온 요청에는 보내지 않는다(기관 코드가 새어 나가는 경로를 줄인다).
    cookie.setAttribute("SameSite", "Lax");
    return cookie;
  }
}
