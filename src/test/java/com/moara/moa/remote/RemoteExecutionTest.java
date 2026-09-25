package com.moara.moa.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

/**
 * 원격 실행 경로의 판단들. 이 패키지는 <b>실제 자격증명이 흐르는 곳</b>인데 테스트가 없었다.
 *
 * <p>여기서 확인하는 것은 네트워크가 아니라 코드가 스스로 내리는 결정이다 — 어떤 채널로
 * 붙을지, 명령을 어떻게 쪼갤지, 실패를 어떻게 알릴지. 실제 SSH·WinRM 서버가 필요한 부분은
 * 단위 테스트의 몫이 아니다(서버 대상 수동 검증은 doc/test-environment.md).
 */
class RemoteExecutionTest {

  // ── 어떤 채널로 붙는가 ────────────────────────────────────────────────────

  /**
   * 모르는 포트는 TLS로 가정한다. 전에는 5986만 TLS여서 HTTPS를 443·8443으로 돌리는 환경에서
   * <b>Basic 인증 헤더가 평문으로 나갔다</b> — base64는 암호화가 아니다.
   */
  @Test
  void onlyDocumentedHttpPortGoesPlaintext() {
    assertFalse(WinRmExecutor.useHttps(5985), "5985는 문서화된 평문 기본값");
    assertTrue(WinRmExecutor.useHttps(5986), "5986은 TLS 기본값");
    assertTrue(WinRmExecutor.useHttps(443), "비표준 HTTPS 포트도 TLS로 봐야 한다");
    assertTrue(WinRmExecutor.useHttps(8443));
    assertTrue(WinRmExecutor.useHttps(5987));
  }

  /** 프로토콜을 안 적으면 SSH다(기존 호출부 호환). */
  @Test
  void targetDefaultsToSsh() {
    assertEquals(RemoteProtocol.SSH,
        new RemoteTarget("10.0.0.1", 22, "root", "secret").protocol());
  }

  /** 디스패처는 프로토콜만 보고 갈라야 한다 — 주입받는 쪽은 채널을 몰라도 된다. */
  @Test
  void dispatcherRoutesByProtocol() {
    RecordingSsh ssh = new RecordingSsh();
    RecordingWinRm winRm = new RecordingWinRm();
    RemoteExecutorDispatcher dispatcher = new RemoteExecutorDispatcher(ssh, winRm);

    dispatcher.execute(new RemoteTarget("h", 22, "u", "s", RemoteProtocol.SSH), "uptime");
    assertEquals("uptime", ssh.lastCommand);
    assertNull(winRm.lastCommand, "SSH 대상인데 WinRM이 불렸다");

    dispatcher.execute(new RemoteTarget("h", 5986, "u", "s", RemoteProtocol.WINRM), "ipconfig");
    assertEquals("ipconfig", winRm.lastCommand);
  }

  // ── 명령을 어떻게 쪼개는가 ────────────────────────────────────────────────

  /**
   * WinRM은 실행 파일과 인자를 따로 받는다. 윈도우 경로에는 공백이 흔한데, 전에는 첫 공백에서
   * 무조건 잘라 따옴표 안이 갈라졌다 — 실행될 수 없는 조합이 만들어졌다.
   */
  @Test
  void splitsQuotedExecutablePathWhole() {
    String[] parts = WinRmClient.splitCommand("\"C:\\Program Files\\App\\run.exe\" -start now");

    assertEquals("\"C:\\Program Files\\App\\run.exe\"", parts[0]);
    assertEquals("-start now", parts[1]);
  }

  @Test
  void splitsPlainCommand() {
    assertEquals("ipconfig", WinRmClient.splitCommand("ipconfig")[0]);
    assertEquals("", WinRmClient.splitCommand("ipconfig")[1]);

    String[] parts = WinRmClient.splitCommand("  sc  query  W32Time ");
    assertEquals("sc", parts[0]);
    assertEquals("query  W32Time", parts[1], "인자 사이 공백은 그대로 넘긴다");
  }

  /** 따옴표가 닫히지 않으면 전체를 실행 파일로 본다(임의로 잘라 내지 않는다). */
  @Test
  void unclosedQuoteIsNotSplit() {
    String raw = "\"C:\\no\\closing quote here";

    String[] parts = WinRmClient.splitCommand(raw);

    assertEquals(raw, parts[0]);
    assertEquals("", parts[1]);
  }

  /** 이스케이프를 빠뜨리면 명령 하나가 SOAP 봉투를 깨뜨린다. */
  @Test
  void escapesXmlSpecialCharacters() {
    assertEquals("dir &amp; echo x", WinRmClient.xml("dir & echo x"));
    assertEquals("a &lt;b&gt; &quot;c&quot; &apos;d&apos;", WinRmClient.xml("a <b> \"c\" 'd'"));
  }

  // ── 실패를 어떻게 알리는가 ────────────────────────────────────────────────

  /**
   * 붙지 못하면 {@link RemoteExecutionException}으로 감싸 올린다. 그리고 <b>예외 사슬에
   * 비밀번호가 없어야 한다</b> — 이 예외는 화면과 로그로 나간다(AGENTS.md: secret은 예외
   * 메시지에 노출 금지).
   */
  @Test
  void sshFailureIsWrappedAndCarriesNoSecret() throws IOException {
    int closedPort = freePort();
    String secret = "p@ssw0rd-should-never-appear";

    RemoteExecutionException thrown = assertThrows(RemoteExecutionException.class,
        () -> new JschSshExecutor()
            .execute(new RemoteTarget("127.0.0.1", closedPort, "root", secret), "uptime"));

    assertFalse(chainText(thrown).contains(secret), "예외 사슬에 비밀번호가 섞였다");
    assertTrue(thrown.getMessage().contains("원격 명령 실행 실패"));
  }

  /** SSH가 아닌 서비스에 붙어도 마찬가지다 — 핸드셰이크 실패가 그대로 새어 나가면 안 된다. */
  @Test
  void sshAgainstNonSshServiceIsWrapped() throws IOException {
    String secret = "another-secret-value";
    try (ServerSocket server = new ServerSocket(0)) {
      Thread accepter = new Thread(() -> {
        try {
          server.accept().close();   // 받자마자 끊는다
        } catch (IOException ignored) {
          // 테스트가 끝나며 소켓이 닫히는 정상 경로
        }
      });
      accepter.setDaemon(true);
      accepter.start();

      RemoteExecutionException thrown = assertThrows(RemoteExecutionException.class,
          () -> new JschSshExecutor().execute(
              new RemoteTarget("127.0.0.1", server.getLocalPort(), "root", secret), "uptime"));

      assertFalse(chainText(thrown).contains(secret));
    }
  }

  /** 성공 판정은 종료코드 0 하나로만. */
  @Test
  void successIsExitCodeZeroOnly() {
    assertTrue(new ExecResult(0, "ok").success());
    assertFalse(new ExecResult(1, "err").success());
    assertFalse(new ExecResult(-1, "").success(), "-1(응답 없음)도 성공이 아니다");
  }

  // ── 도우미 ────────────────────────────────────────────────────────────────

  private static String chainText(Throwable thrown) {
    StringBuilder text = new StringBuilder();
    for (Throwable t = thrown; t != null; t = t.getCause()) {
      text.append(t.getMessage()).append('\n').append(t).append('\n');
    }
    return text.toString();
  }

  /** 아무도 듣지 않는 포트. 열었다 바로 닫아 번호만 얻는다. */
  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static class RecordingSsh extends JschSshExecutor {
    String lastCommand;

    @Override
    public ExecResult execute(RemoteTarget target, String command) {
      lastCommand = command;
      return new ExecResult(0, "");
    }
  }

  private static class RecordingWinRm extends WinRmExecutor {
    String lastCommand;

    @Override
    public ExecResult execute(RemoteTarget target, String command) {
      lastCommand = command;
      return new ExecResult(0, "");
    }
  }
}
