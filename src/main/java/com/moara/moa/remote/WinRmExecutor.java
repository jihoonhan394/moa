package com.moara.moa.remote;

import org.springframework.stereotype.Component;

/**
 * WinRM(WS-Management) 원격 실행기 — 외부 SOAP 스택 없이 우리가 소유한 최소 구현({@link WinRmClient}).
 * 윈도우 네이티브 관리 채널이라 OpenSSH가 없어도 GPO로 대량 배포된 WinRM을 그대로 쓸 수 있다.
 * 포트 5986은 HTTPS, 그 외(5985)는 HTTP로 처리한다. 자격증명은 즉시 사용·폐기하며 로그에 남기지 않는다.
 */
@Component
public class WinRmExecutor implements RemoteExecutor {

  /** WinRM 평문(HTTP) 기본 포트. 마이크로소프트가 정한 값이다. */
  static final int HTTP_PORT = 5985;

  @Override
  public ExecResult execute(RemoteTarget target, String command) {
    WinRmClient client = new WinRmClient(
        target.host(), target.port(), useHttps(target.port()),
        target.username(), target.secret());
    return client.run(command);
  }

  /**
   * 평문으로 붙을지 TLS로 붙을지 정한다. <b>5985(문서화된 HTTP 기본값)만 평문</b>이고 나머지는
   * 전부 TLS다.
   *
   * <p>전에는 {@code port == 5986}만 TLS였다. 그래서 HTTPS를 5986이 아닌 포트(443·8443 등)로
   * 돌리는 환경에서 <b>Basic 인증 헤더가 평문으로 나갔다</b> — base64는 암호화가 아니므로
   * 계정과 비밀번호가 그대로 선로에 실린다. 모르는 포트는 안전한 쪽으로 가정한다. 정말
   * 평문이어야 한다면 TLS 오류로 즉시 드러나지, 비밀이 조용히 새지 않는다.
   */
  static boolean useHttps(int port) {
    return port != HTTP_PORT;
  }
}
