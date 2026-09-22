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
 * 로그에 남기지 않는다. StrictHostKeyChecking은 제어/데모 편의상 off(운영은 known_hosts 관리 권장).
 */
@Component
public class JschSshExecutor implements RemoteExecutor {
  private static final int CONNECT_TIMEOUT_MS = 15_000;
  private static final long READ_TIMEOUT_MS = 60_000;

  @Override
  public ExecResult execute(RemoteTarget target, String command) {
    Session session = null;
    ChannelExec channel = null;
    try {
      JSch jsch = new JSch();
      session = jsch.getSession(target.username(), target.host(), target.port());
      session.setPassword(target.secret());
      Properties config = new Properties();
      config.put("StrictHostKeyChecking", "no");
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
        Thread.sleep(100);
      }
      int exitCode = channel.getExitStatus();
      return new ExecResult(exitCode, output.toString(StandardCharsets.UTF_8).trim());
    } catch (Exception exception) {
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
}
