package com.moara.moa.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 요청의 실제 출발지를 정한다. 로그인 잠금과 감사 기록이 이 값을 쓴다.
 *
 * <h2>왜 홉 수를 설정으로 받나</h2>
 *
 * <p>{@code X-Forwarded-For} 는 <b>클라이언트가 보낼 수 있는 헤더</b>다. 그걸 그대로 믿으면 두
 * 가지가 깨진다.
 *
 * <ul>
 *   <li><b>출발지 잠금 우회</b> — 값을 바꿔 가며 보내면 매 시도가 다른 출발지로 집계된다
 *   <li><b>감사 기록 오염</b> — 남의 IP 를 적어 보낼 수 있다. PAM 에서 감사는 산출물 자체이고,
 *       지울 수 없게 지켜도 처음부터 거짓이 들어가면 의미가 없다
 *   <li>반대로 프록시가 헤더를 안 넘기면 모든 요청이 프록시 IP 하나로 보여
 *       <b>한 사람이 20번 틀리면 전체 사용자가 잠긴다</b>
 * </ul>
 *
 * <p>그래서 "믿을 수 있는 프록시가 몇 홉인지"를 운영자가 명시한다
 * ({@code moa.trusted-proxy-count}). <b>기본값은 0</b> — 설정하지 않은 환경은 헤더를 아예 믿지
 * 않고 TCP 연결의 상대 주소만 쓴다. 안전한 쪽이 기본값이다.
 *
 * <h2>왜 뒤에서부터 세나</h2>
 *
 * <p>프록시는 자기가 받은 연결의 상대 주소를 체인 <b>끝에</b> 덧붙인다. 그래서 신뢰 홉이 N 이면
 * 우리가 찾는 값은 끝에서 N 번째다.
 *
 * <pre>
 *   N=1  "203.0.113.9"                  → 203.0.113.9
 *   N=1  "1.2.3.4, 203.0.113.9"         → 203.0.113.9   (앞의 위조값은 무시된다)
 *   N=2  "203.0.113.9, 10.0.0.8"        → 203.0.113.9
 * </pre>
 *
 * <p>앞에서부터 고르면(예전 방식: "체인 중 첫 공인 IP") 클라이언트가 체인 앞에 아무 값이나
 * 끼워 넣을 수 있어 구조적으로 위조에 열려 있다. 뒤에서 세는 방식은 nginx 가 덮어쓰든
 * ({@code $remote_addr}) 덧붙이든({@code $proxy_add_x_forwarded_for}) 둘 다 안전하다.
 *
 * <p>체인이 기대한 홉 수보다 짧으면 설정과 실제가 어긋난 것이므로 헤더를 쓰지 않는다.
 */
@Component
public class ClientIpResolver {

  private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

  /** {@code login_failures.client_ip} 가 VARCHAR(64) 다. 그보다 긴 값은 출발지가 아니다. */
  private static final int MAX_LENGTH = 64;

  private final int trustedProxyCount;

  public ClientIpResolver(@Value("${moa.trusted-proxy-count:0}") int trustedProxyCount) {
    this.trustedProxyCount = Math.max(0, trustedProxyCount);
  }

  public String resolve(HttpServletRequest request) {
    String direct = trim(request.getRemoteAddr());
    if (trustedProxyCount == 0) {
      // 프록시가 없다고 선언한 환경. 헤더는 전부 클라이언트가 보낸 것으로 본다.
      return direct;
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded == null || forwarded.isBlank()) {
      return direct;
    }
    String[] hops = forwarded.split(",");
    int index = hops.length - trustedProxyCount;
    if (index < 0) {
      // 선언한 홉 수보다 체인이 짧다 — 설정과 실제가 어긋났다. 헤더를 믿지 않는다.
      return direct;
    }
    String candidate = trim(hops[index]);
    return candidate.isEmpty() ? direct : candidate;
  }

  /**
   * 홉 수를 맞출 때만 잠깐 켠다.
   *
   * <pre>
   *   logging.level.com.moara.moa.security.ClientIpResolver=DEBUG
   * </pre>
   *
   * <p>프록시·터널을 새로 붙이면 체인이 몇 칸인지 눈으로 봐야 {@code moa.trusted-proxy-count}
   * 를 맞출 수 있다. <b>기본은 꺼져 있다</b> — 요청마다 출발지를 남기면 그 자체가 접속 기록이
   * 되고 로그가 커진다. 맞춘 뒤에는 다시 끈다.
   */
  public void logChain(HttpServletRequest request) {
    if (!log.isDebugEnabled()) {
      return;
    }
    log.debug("[출발지] trustedProxyCount={} remoteAddr={} X-Forwarded-For={} -> {}",
        trustedProxyCount, request.getRemoteAddr(),
        request.getHeader("X-Forwarded-For"), resolve(request));
  }

  private static String trim(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
  }
}
