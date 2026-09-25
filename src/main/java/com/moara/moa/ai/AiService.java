package com.moara.moa.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 기관 AI 설정으로 텍스트를 생성한다. 제공자별 요청 포맷을 분기한다:
 *  - OpenAI/DeepSeek: /v1/chat/completions (Bearer 키)
 *  - Claude: /v1/messages (x-api-key + anthropic-version)
 *  - Gemini: /v1beta/models/{model}:generateContent?key=
 * 폐쇄망은 base_url을 로컬 OpenAI 호환 엔드포인트로 바꾸면 그대로 동작. 키·URL은 로그/예외에 담지 않는다.
 */
@Service
public class AiService {
  private final AiSettingService settingService;
  private final HttpClient httpClient;
  private final int requestTimeoutSeconds;
  private final ObjectMapper mapper = new ObjectMapper();

  /** 연결 확인용 프롬프트. 왕복만 증명하면 되므로 토큰을 거의 쓰지 않게 짧게 둔다. */
  private static final String PROBE_PROMPT =
      "연결 확인입니다. 다른 말 없이 «연결 확인됨» 이라고만 답하세요.";

  public AiService(
      AiSettingService settingService,
      @Value("${moa.ai.connect-timeout-seconds:10}") int connectTimeoutSeconds,
      @Value("${moa.ai.request-timeout-seconds:60}") int requestTimeoutSeconds) {
    this.settingService = settingService;
    this.requestTimeoutSeconds = requestTimeoutSeconds;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds)).build();
  }

  public boolean isConfigured(UUID tenantId) {
    return settingService.findForTenant(tenantId).filter(s -> s.isEnabled() && s.hasSecret()).isPresent();
  }

  /** 프롬프트로 텍스트 생성. 설정이 없거나 실패하면 AiException. */
  public String generate(UUID tenantId, String prompt) {
    AiSetting setting = settingService.findForTenant(tenantId)
        .filter(s -> s.isEnabled() && s.hasSecret())
        .orElseThrow(AiNotConfiguredException::new);
    String apiKey = settingService.decryptApiKey(setting);
    try {
      String text = switch (setting.getProvider()) {
        case OPENAI, DEEPSEEK -> callOpenAiCompatible(setting, apiKey, prompt);
        case CLAUDE -> callClaude(setting, apiKey, prompt);
        case GEMINI -> callGemini(setting, apiKey, prompt);
      };
      if (text == null || text.isBlank()) {
        throw new AiException("AI 응답이 비어 있습니다.");
      }
      return text.trim();
    } catch (AiException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AiException("AI 호출이 중단되었습니다.");
    } catch (java.net.http.HttpTimeoutException timeout) {
      throw new AiException("응답 시간 초과(" + requestTimeoutSeconds
          + "초). 네트워크와 Base URL을 확인하세요. 폐쇄망이면 프록시·방화벽도 함께 보세요.");
    } catch (java.io.IOException unreachable) {
      throw new AiException("제공자에 연결할 수 없습니다. Base URL과 방화벽을 확인하세요.");
    } catch (Exception exception) {
      // 예외 메시지에는 URL이 실릴 수 있고 Gemini는 URL 쿼리에 키를 담는다 → 종류만 남긴다.
      throw new AiException("AI 호출 실패(" + exception.getClass().getSimpleName() + ").");
    }
  }

  /**
   * 설정이 실제로 동작하는지 왕복으로 확인한다.
   *
   * <p>저장만으로는 아무것도 증명되지 않는다 — 키가 틀려도 화면은 "저장했습니다"라고 하고,
   * 문제는 한참 뒤 다른 기능에서 조용한 비활성으로 나타난다. 짧은 프롬프트 한 번이면
   * 키·모델명·주소·네트워크가 한꺼번에 확인된다.
   */
  public TestResult test(UUID tenantId) {
    AiSetting setting = settingService.findForTenant(tenantId).orElse(null);
    if (setting == null || !setting.hasSecret()) {
      return TestResult.failed("API 키가 저장되어 있지 않습니다. 키를 입력하고 저장하세요.", null, null);
    }
    String model = setting.effectiveModel();
    String baseUrl = setting.effectiveBaseUrl();
    if (!setting.isEnabled()) {
      return TestResult.failed(
          "설정이 비활성 상태입니다. 'AI 기능 사용'을 켜야 각 화면에서 쓰입니다.", model, baseUrl);
    }
    try {
      String reply = generate(tenantId, PROBE_PROMPT);
      return new TestResult(true, "연결에 성공했습니다.", model, baseUrl, shorten(reply));
    } catch (AiException failure) {
      return TestResult.failed(failure.getMessage(), model, baseUrl);
    }
  }

  /** 응답이 길어도 화면은 앞부분만 있으면 된다 — 왕복이 됐다는 증거면 충분하다. */
  private static String shorten(String reply) {
    String trimmed = reply.strip();
    return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "…";
  }

  /**
   * 연결 확인 결과. 성공·실패 모두 <b>무엇으로 붙었는지</b>(모델·주소)를 같이 돌려준다 —
   * 비워 둔 칸이 제공자 기본값으로 채워지므로, 사용자가 자기가 입력한 것과 실제로 쓰인 것을
   * 견줄 수 있어야 한다. 키는 담지 않는다.
   */
  public record TestResult(boolean ok, String message, String model, String baseUrl, String reply) {
    static TestResult failed(String message, String model, String baseUrl) {
      return new TestResult(false, message, model, baseUrl, null);
    }
  }

  private String callOpenAiCompatible(AiSetting setting, String apiKey, String prompt) throws Exception {
    ObjectNode body = mapper.createObjectNode();
    body.put("model", setting.effectiveModel());
    ArrayNode messages = body.putArray("messages");
    ObjectNode message = messages.addObject();
    message.put("role", "user");
    message.put("content", prompt);
    HttpRequest request = jsonPost(setting.effectiveBaseUrl() + "/v1/chat/completions", body)
        .header("Authorization", "Bearer " + apiKey)
        .build();
    JsonNode root = send(request);
    return root.path("choices").path(0).path("message").path("content").asText(null);
  }

  private String callClaude(AiSetting setting, String apiKey, String prompt) throws Exception {
    ObjectNode body = mapper.createObjectNode();
    body.put("model", setting.effectiveModel());
    body.put("max_tokens", 2000);
    ArrayNode messages = body.putArray("messages");
    ObjectNode message = messages.addObject();
    message.put("role", "user");
    message.put("content", prompt);
    HttpRequest request = jsonPost(setting.effectiveBaseUrl() + "/v1/messages", body)
        .header("x-api-key", apiKey)
        .header("anthropic-version", "2023-06-01")
        .build();
    JsonNode root = send(request);
    return root.path("content").path(0).path("text").asText(null);
  }

  private String callGemini(AiSetting setting, String apiKey, String prompt) throws Exception {
    ObjectNode body = mapper.createObjectNode();
    ArrayNode contents = body.putArray("contents");
    ObjectNode content = contents.addObject();
    content.putArray("parts").addObject().put("text", prompt);
    String url = setting.effectiveBaseUrl() + "/v1beta/models/" + setting.effectiveModel()
        + ":generateContent?key=" + apiKey;
    HttpRequest request = jsonPost(url, body).build();
    JsonNode root = send(request);
    return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
  }

  private HttpRequest.Builder jsonPost(String url, ObjectNode body) throws Exception {
    return HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(requestTimeoutSeconds))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
  }

  private JsonNode send(HttpRequest request) throws Exception {
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) {
      throw new AiException(explain(response.statusCode()));
    }
    return mapper.readTree(response.body());
  }

  /**
   * 상태 코드를 <b>고칠 수 있는 말</b>로 바꾼다. "AI 제공자 오류(HTTP 401)"만으로는 키가
   * 틀렸는지 모델명이 틀렸는지 알 수 없어, 사용자가 설정 화면에서 할 일을 찾지 못한다.
   *
   * <p>응답 본문과 요청 URL은 절대 담지 않는다 — 본문은 키를 되비추는 제공자가 있고,
   * Gemini는 <b>URL 쿼리에 키가 들어간다</b>.
   */
  private static String explain(int status) {
    return switch (status) {
      case 401, 403 -> "API 키가 거부되었습니다(HTTP " + status
          + "). 키가 맞는지, 선택한 제공자의 키가 맞는지 확인하세요.";
      case 404 -> "모델이나 주소를 찾을 수 없습니다(HTTP 404). 모델명과 Base URL을 확인하세요.";
      case 429 -> "요청 한도를 넘었습니다(HTTP 429). 잠시 후 다시 시도하거나 제공자 사용량을 확인하세요.";
      default -> status / 100 == 5
          ? "제공자 서버 오류(HTTP " + status + "). 잠시 후 다시 시도하세요."
          : "제공자가 요청을 거부했습니다(HTTP " + status + "). 모델명과 설정을 확인하세요.";
    };
  }
}
