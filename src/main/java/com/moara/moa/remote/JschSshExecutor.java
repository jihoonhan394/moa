package com.moara.moa.remote;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.stereotype.Component;

/**
 * SSH(JSch, 비밀번호 인증)로 원격 명령을 실행한다. 자격증명은 {@link RemoteTarget}로 즉시 전달·사용·폐기하며
 * 로그에 남기지 않는다.
 *
 * <p>호스트 신원은 TOFU로 확인한다({@link TofuHostKeyRepository}) — 처음 본 호스트는 기록하고
 * 통과, 기록과 다르면 막는다. 전에는 {@code StrictHostKeyChecking=no}여서 그 주소에 답하는
 * 누구에게나 볼트에서 꺼낸 평문 비밀번호를 건넸다.
 */
@Component
public class JschSshExecutor implements RemoteExecutor {
  private static final int CONNECT_TIMEOUT_MS = 15_000;
  private static final long READ_TIMEOUT_MS = 60_000;
  private static final long POLL_INTERVAL_MS = 100;

  private final RemoteHostKeyStore hostKeyStore;

  public JschSshExecutor(RemoteHostKeyStore hostKeyStore) {
    this.hostKeyStore = hostKeyStore;
  }

  @Override
  public ExecResult execute(RemoteTarget target, String command) {
    Session session = null;
    ChannelExec channel = null;
    try {
      JSch jsch = new JSch();
      jsch.setHostKeyRepository(new TofuHostKeyRepository(
          hostKeyStore, target.tenantId(), target.host(), target.port()));
      session = jsch.getSession(target.username(), target.host(), target.port());
      session.setPassword(target.secret());
      Properties config = new Properties();
      // 검사를 켜 두고, 무엇을 통과시킬지는 위 저장소가 정한다.
      config.put("StrictHostKeyChecking", "yes");
      session.setConfig(config);
      session.connect(CONNECT_TIMEOUT_MS);

      channel = (ChannelExec) session.openChannel("exec");
      channel.setCommand(command);
      channel.setPty(false);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      channel.setOutputStream(output);
      channel.setErrStream(output);
      channel.connect();

      long deadline = System.currentTimeMillis() + READ_TIMEOUT_MS;
      while (!channel.isClosed() && System.currentTimeMillis() < deadline) {
        Thread.sleep(POLL_INTERVAL_MS);
      }
      // 기다리다 포기한 것과 명령이 실패한 것은 다른 사건이다. 전에는 둘 다
      // ExecResult(-1, ...)로 나갔다 — 아직 돌고 있는 채널의 getExitStatus()가 -1이기
      // 때문이다. 솔루션 기동처럼 느린 명령이 "실패"로 보고되면 운영자가 잘못 판단한다.
      if (!channel.isClosed()) {
        throw new RemoteExecutionException(
            "원격 명령 응답 시간 초과(" + (READ_TIMEOUT_MS / 1000) + "초) — 명령은 계속 돌고 있을 수 있다");
      }
      int exitCode = channel.getExitStatus();
      return new ExecResult(exitCode, output.toString(StandardCharsets.UTF_8).trim());
    } catch (RemoteExecutionException alreadyOurs) {
      throw alreadyOurs;
    } catch (InterruptedException interrupted) {
      // 인터럽트 플래그를 삼키면 이 스레드를 멈추려는 상위 요청이 사라진다.
      Thread.currentThread().interrupt();
      throw new RemoteExecutionException("원격 명령 실행이 중단되었습니다", interrupted);
    } catch (Exception exception) {
      // JSch는 연결 중 튀어나온 예외를 자기 JSchException으로 감싼다. 신원 불일치는
      // 관리자가 읽고 판단해야 하는 메시지라, 감싸인 원인을 그대로 꺼내 올린다.
      HostIdentityMismatchException mismatch = mismatchIn(exception);
      if (mismatch != null) {
        throw mismatch;
      }
      throw new RemoteExecutionException("원격 명령 실행 실패: " + exception.getMessage(), exception);
    } finally {
      if (channel != null) {
        channel.disconnect();
      }
      if (session != null) {
        session.disconnect();
      }
    }
  }

  private static HostIdentityMismatchException mismatchIn(Throwable thrown) {
    for (Throwable t = thrown; t != null; t = t.getCause()) {
      if (t instanceof HostIdentityMismatchException mismatch) {
        return mismatch;
      }
    }
    return null;
  }
}
