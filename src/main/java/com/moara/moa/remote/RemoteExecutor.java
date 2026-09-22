package com.moara.moa.remote;

/** 원격 호스트에서 명령을 실행하는 어댑터(SSH 등). 구현을 교체해 전송 방식을 바꾼다. */
public interface RemoteExecutor {
  ExecResult execute(RemoteTarget target, String command);
}
