package com.moara.moa.remote;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 대상의 {@link RemoteProtocol}에 따라 SSH/WinRM 실행기로 라우팅한다.
 * {@link RemoteExecutor} 주입 지점은 이 디스패처(@Primary)를 받아 프로토콜을 신경 쓰지 않는다.
 */
@Component
@Primary
public class RemoteExecutorDispatcher implements RemoteExecutor {
  private final JschSshExecutor sshExecutor;
  private final WinRmExecutor winRmExecutor;

  public RemoteExecutorDispatcher(JschSshExecutor sshExecutor, WinRmExecutor winRmExecutor) {
    this.sshExecutor = sshExecutor;
    this.winRmExecutor = winRmExecutor;
  }

  @Override
  public ExecResult execute(RemoteTarget target, String command) {
    return switch (target.protocol()) {
      case SSH -> sshExecutor.execute(target, command);
      case WINRM -> winRmExecutor.execute(target, command);
    };
  }
}
