package com.moara.moa.remote;

import org.springframework.stereotype.Component;

/**
 * WinRM(WS-Management) 원격 실행기 — 외부 SOAP 스택 없이 우리가 소유한 최소 구현({@link WinRmClient}).
 * 윈도우 네이티브 관리 채널이라 OpenSSH가 없어도 GPO로 대량 배포된 WinRM을 그대로 쓸 수 있다.
 * 포트 5986은 HTTPS, 그 외(5985)는 HTTP로 처리한다. 자격증명은 즉시 사용·폐기하며 로그에 남기지 않는다.
 */
@Component
public class WinRmExecutor implements RemoteExecutor {

  @Override
  public ExecResult execute(RemoteTarget target, String command) {
    boolean https = target.port() == 5986;
    WinRmClient client =
        new WinRmClient(target.host(), target.port(), https, target.username(), target.secret());
    return client.run(command);
  }
}
