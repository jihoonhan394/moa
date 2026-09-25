package com.moara.moa.remote;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 최소 WinRM(WS-Management, MS-WSMV) 클라이언트. 외부 SOAP 스택 없이 JDK {@link HttpClient}로만 구현한다.
 * cmd Shell을 열어 명령을 실행하고 stdout/stderr를 수신한 뒤 Shell을 닫는다.
 * 인증은 Basic을 사용한다 — 운영은 HTTPS(5986)를 전제로 하고, 평문(HTTP)은 개발/검증용이다.
 * XML 파싱은 JDK 내장 {@code java.xml}(javax.xml.parsers)만 사용하므로 jakarta 전환과 무관하다.
 */
class WinRmClient {
  private static final String NS_SOAP = "http://www.w3.org/2003/05/soap-envelope";
  private static final String NS_ADDR = "http://schemas.xmlsoap.org/ws/2004/08/addressing";
  private static final String NS_WSMAN = "http://schemas.dmtf.org/wbem/wsman/1/wsman.xsd";
  private static final String NS_SHELL = "http://schemas.microsoft.com/wbem/wsman/1/windows/shell";
  private static final String RES_CMD = "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/cmd";
  private static final String A_CREATE = "http://schemas.xmlsoap.org/ws/2004/09/transfer/Create";
  private static final String A_DELETE = "http://schemas.xmlsoap.org/ws/2004/09/transfer/Delete";
  private static final String A_COMMAND = NS_SHELL + "/Command";
  private static final String A_RECEIVE = NS_SHELL + "/Receive";
  private static final String A_SIGNAL = NS_SHELL + "/Signal";
  private static final String SIG_TERMINATE = NS_SHELL + "/signal/terminate";
  private static final int MAX_RECEIVE_LOOPS = 600;

  private final HttpClient http;
  private final URI endpoint;
  private final String authHeader;

  WinRmClient(String host, int port, boolean https, String username, String secret) {
    this.endpoint = URI.create((https ? "https" : "http") + "://" + host + ":" + port + "/wsman");
    this.authHeader = "Basic " + Base64.getEncoder()
        .encodeToString((username + ":" + secret).getBytes(StandardCharsets.UTF_8));
    HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15));
    if (https) {
      builder.sslContext(trustAllContext());
    }
    this.http = builder.build();
  }

  /** cmd Shell을 열어 command를 실행하고 stdout+stderr와 종료코드를 반환한다. */
  ExecResult run(String command) {
    String shellId = createShell();
    try {
      String commandId = runCommand(shellId, command);
      return receive(shellId, commandId);
    } finally {
      try {
        deleteShell(shellId);
      } catch (RuntimeException ignored) {
        // 정리 실패는 결과에 영향 주지 않음
      }
    }
  }

  private String createShell() {
    String body = "<rsp:Shell xmlns:rsp='" + NS_SHELL + "'>"
        + "<rsp:InputStreams>stdin</rsp:InputStreams>"
        + "<rsp:OutputStreams>stdout stderr</rsp:OutputStreams></rsp:Shell>";
    String options = "<w:OptionSet xmlns:w='" + NS_WSMAN + "'>"
        + "<w:Option Name='WINRS_NOPROFILE'>FALSE</w:Option>"
        + "<w:Option Name='WINRS_CODEPAGE'>437</w:Option></w:OptionSet>";
    Document response = post(envelope(A_CREATE, null, options, body));
    String shellId = firstText(response, NS_SHELL, "ShellId");
    if (shellId == null) {
      shellId = firstText(response, "http://schemas.xmlsoap.org/ws/2004/09/transfer", "ResourceCreated");
    }
    if (shellId == null) {
      throw new RemoteExecutionException("WinRM Shell 생성 실패: " + faultOr(response));
    }
    return shellId;
  }

  private String runCommand(String shellId, String command) {
    String[] parts = splitCommand(command);
    String body = "<rsp:CommandLine xmlns:rsp='" + NS_SHELL + "'>"
        + "<rsp:Command>" + xml(parts[0]) + "</rsp:Command>"
        + (parts[1].isEmpty() ? "" : "<rsp:Arguments>" + xml(parts[1]) + "</rsp:Arguments>")
        + "</rsp:CommandLine>";
    Document response = post(envelope(A_COMMAND, shellId, null, body));
    String commandId = firstText(response, NS_SHELL, "CommandId");
    if (commandId == null) {
      throw new RemoteExecutionException("WinRM 명령 실행 실패: " + faultOr(response));
    }
    return commandId;
  }

  private ExecResult receive(String shellId, String commandId) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < MAX_RECEIVE_LOOPS; i++) {
      String body = "<rsp:Receive xmlns:rsp='" + NS_SHELL + "'>"
          + "<rsp:DesiredStream CommandId='" + commandId + "'>stdout stderr</rsp:DesiredStream>"
          + "</rsp:Receive>";
      Document response = post(envelope(A_RECEIVE, shellId, null, body));
      NodeList streams = response.getElementsByTagNameNS(NS_SHELL, "Stream");
      for (int s = 0; s < streams.getLength(); s++) {
        String text = streams.item(s).getTextContent();
        if (text != null && !text.isBlank()) {
          out.append(new String(Base64.getMimeDecoder().decode(text.trim()), StandardCharsets.UTF_8));
        }
      }
      Element state = firstElement(response, NS_SHELL, "CommandState");
      if (state != null && state.getAttribute("State").endsWith("/Done")) {
        int exit = parseInt(firstText(response, NS_SHELL, "ExitCode"), -1);
        signalTerminate(shellId, commandId);
        return new ExecResult(exit, out.toString().trim());
      }
    }
    throw new RemoteExecutionException("WinRM 출력 수신 타임아웃");
  }

  private void signalTerminate(String shellId, String commandId) {
    String body = "<rsp:Signal xmlns:rsp='" + NS_SHELL + "' CommandId='" + commandId + "'>"
        + "<rsp:Code>" + SIG_TERMINATE + "</rsp:Code></rsp:Signal>";
    post(envelope(A_SIGNAL, shellId, null, body));
  }

  private void deleteShell(String shellId) {
    post(envelope(A_DELETE, shellId, null, ""));
  }

  /** 공통 헤더를 채운 SOAP 봉투. selector가 있으면 SelectorSet(ShellId), options가 있으면 헤더에 넣는다. */
  private String envelope(String action, String shellId, String options, String body) {
    String selector = shellId == null ? "" : "<w:SelectorSet xmlns:w='" + NS_WSMAN + "'>"
        + "<w:Selector Name='ShellId'>" + shellId + "</w:Selector></w:SelectorSet>";
    return "<s:Envelope xmlns:s='" + NS_SOAP + "' xmlns:a='" + NS_ADDR + "' xmlns:w='" + NS_WSMAN + "'>"
        + "<s:Header>"
        + "<a:To>" + endpoint + "</a:To>"
        + "<w:ResourceURI s:mustUnderstand='true'>" + RES_CMD + "</w:ResourceURI>"
        + "<a:ReplyTo><a:Address s:mustUnderstand='true'>"
        + NS_ADDR + "/role/anonymous</a:Address></a:ReplyTo>"
        + "<w:MaxEnvelopeSize s:mustUnderstand='true'>153600</w:MaxEnvelopeSize>"
        + "<a:MessageID>uuid:" + UUID.randomUUID() + "</a:MessageID>"
        + "<w:Locale xml:lang='en-US' s:mustUnderstand='false'/>"
        + "<w:OperationTimeout>PT60S</w:OperationTimeout>"
        + "<a:Action s:mustUnderstand='true'>" + action + "</a:Action>"
        + selector
        + (options == null ? "" : options)
        + "</s:Header><s:Body>" + body + "</s:Body></s:Envelope>";
  }

  private Document post(String soap) {
    try {
      HttpRequest request = HttpRequest.newBuilder(endpoint)
          .timeout(Duration.ofSeconds(70))
          .header("Content-Type", "application/soap+xml;charset=UTF-8")
          .header("Authorization", authHeader)
          .POST(HttpRequest.BodyPublishers.ofString(soap, StandardCharsets.UTF_8))
          .build();
      HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
      byte[] payload = response.body();
      if (response.statusCode() == 401) {
        throw new RemoteExecutionException("WinRM 인증 실패(401) — Basic 허용/자격증명 확인");
      }
      Document document = parse(payload);
      if (response.statusCode() >= 400 && document == null) {
        throw new RemoteExecutionException("WinRM HTTP " + response.statusCode());
      }
      return document;
    } catch (RemoteExecutionException e) {
      throw e;
    } catch (Exception e) {
      throw new RemoteExecutionException("WinRM 통신 실패: " + e.getMessage(), e);
    }
  }

  private static Document parse(byte[] xml) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      return builder.parse(new ByteArrayInputStream(xml));
    } catch (Exception e) {
      return null;
    }
  }

  private static String firstText(Document doc, String ns, String local) {
    Element e = firstElement(doc, ns, local);
    return e == null ? null : e.getTextContent().trim();
  }

  private static Element firstElement(Document doc, String ns, String local) {
    if (doc == null) {
      return null;
    }
    NodeList nodes = doc.getElementsByTagNameNS(ns, local);
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (node instanceof Element element) {
        return element;
      }
    }
    return null;
  }

  private static String faultOr(Document doc) {
    String reason = firstText(doc, NS_SOAP, "Text");
    return reason == null ? "(알 수 없는 응답)" : reason;
  }

  /**
   * 명령을 실행 파일(첫 토큰)과 인자(나머지)로 분리한다. WinRM CommandLine 스키마는 둘을
   * 따로 받는다.
   *
   * <p>윈도우 경로에는 공백이 흔하다. 전에는 첫 공백에서 무조건 잘라
   * {@code "C:PATH x.exe" -flag}가 실행 파일 {@code "C:PATH}와 인자 {@code x.exe" -flag}로
   * 갈렸다 — 실행될 수 없는 조합이다. 큰따옴표로 감싼 경우에는 닫는 따옴표 뒤의 공백에서
   * 자른다.
   */
  static String[] splitCommand(String command) {
    String trimmed = command.strip();
    int cut;
    if (trimmed.startsWith("\"")) {
      int closing = trimmed.indexOf('\"', 1);
      cut = closing < 0 ? -1 : trimmed.indexOf(' ', closing);
    } else {
      cut = trimmed.indexOf(' ');
    }
    if (cut < 0) {
      return new String[] {trimmed, ""};
    }
    return new String[] {trimmed.substring(0, cut), trimmed.substring(cut + 1).strip()};
  }

  private static int parseInt(String value, int fallback) {
    try {
      return value == null ? fallback : Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  /** SOAP 본문에 넣기 전 XML 특수문자를 막는다. `dir & echo x` 같은 명령이 봉투를 깨뜨린다. */
  static String xml(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;");
  }

  private static SSLContext trustAllContext() {
    try {
      TrustManager[] trustAll = {new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      }};
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustAll, new SecureRandom());
      return context;
    } catch (Exception e) {
      throw new RemoteExecutionException("WinRM TLS 초기화 실패: " + e.getMessage(), e);
    }
  }
}
