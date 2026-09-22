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
    } catch (Exception exception) {
      throw new AiException("AI 호출 실패: " + exception.getClass().getSimpleName());
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
      // 응답 본문/URL은 키를 담을 수 있어 노출하지 않는다.
      throw new AiException("AI 제공자 오류(HTTP " + response.statusCode() + ").");
    }
    return mapper.readTree(response.body());
  }
}
