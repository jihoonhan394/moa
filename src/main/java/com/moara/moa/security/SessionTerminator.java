package com.moara.moa.security;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

/**
 * 한 사람의 열린 세션을 만료시킨다. <b>역할이 바뀌면 즉시 반영되게</b> 하려고 쓴다.
 *
 * <h2>왜 필요한가 — 두 방향 모두 문제였다</h2>
 *
 * 권한은 로그인 시점에 {@link MoaUserDetails} 로 <b>복사</b>되어 세션에 담긴다
 * ({@code private final Set<UserRole> roles}). 그래서 역할을 바꿔도 그 사람의 세션은
 * 예전 권한을 그대로 들고 있었다.
 *
 * <ul>
 *   <li><b>권한을 줬을 때</b> — 사용자는 계속 403 을 보고, 관리자가 "로그아웃하고 다시
 *       들어오세요"를 말로 전해야 했다. 아무 안내가 없으니 "고장났다"로 읽는다.
 *   <li><b>권한을 뺐을 때</b> — <b>이쪽이 더 나쁘다.</b> 회수했는데 그 사람은 로그아웃할
 *       때까지 그 권한을 계속 쓴다. 자격증명 열람·솔루션 제어처럼 회수의 이유가 급한
 *       권한일수록 그 창이 위험하다.
 * </ul>
 *
 * <h2>왜 "권한만 갈아끼우기"가 아니라 세션 만료인가</h2>
 *
 * 요청마다 역할을 다시 읽어 Authentication 을 갈아끼우는 방법도 있다. 그러면 로그아웃이
 * 없지만 <b>모든 요청에 질의가 하나 붙는다</b> — 역할 변경은 몇 달에 한 번인데 그 비용을
 * 매 요청이 낸다. 다시 로그인하는 쪽이 드물고, 무엇보다 <b>권한 회수가 확실하다.</b>
 */
@Component
public class SessionTerminator {

  private static final Logger log = LoggerFactory.getLogger(SessionTerminator.class);

  private final SessionRegistry sessionRegistry;

  public SessionTerminator(SessionRegistry sessionRegistry) {
    this.sessionRegistry = sessionRegistry;
  }

  /**
   * 그 사용자의 모든 세션을 만료 표시한다. 다음 요청에서 로그인 화면으로 간다.
   *
   * @return 만료시킨 세션 수
   */
  public int expireSessionsOf(UUID userId) {
    if (userId == null) {
      return 0;
    }
    int expired = 0;
    for (Object principal : sessionRegistry.getAllPrincipals()) {
      // 레지스트리는 principal 객체로 묶는다. MoaUserDetails 에 equals 가 없으므로
      // 같은 객체인지 비교하지 않고 **사용자 식별자**로 찾는다.
      if (!(principal instanceof MoaUserDetails details)
          || !userId.equals(details.getUserId())) {
        continue;
      }
      // includeExpiredSessions=false — 이미 만료된 것을 또 만료시킬 필요가 없다.
      List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
      for (SessionInformation session : sessions) {
        session.expireNow();
        expired++;
      }
    }
    if (expired > 0) {
      log.info("[세션] 사용자 {} 의 세션 {}개를 만료시켰다(역할 변경).", userId, expired);
    }
    return expired;
  }
}
