package com.moara.moa.ai;

/**
 * AI 제공자. 호출 방식은 세 부류로 나뉜다:
 *  - OPENAI, DEEPSEEK: OpenAI 호환 Chat Completions (/v1/chat/completions, Bearer 키)
 *  - CLAUDE: Anthropic Messages (/v1/messages, x-api-key + anthropic-version)
 *  - GEMINI: Google Generative Language (/v1beta/models/{model}:generateContent?key=)
 * baseUrl·model은 기관 설정에서 덮어쓸 수 있고, 비우면 아래 기본값을 쓴다.
 */
public enum AiProvider {
  GEMINI("Gemini", "https://generativelanguage.googleapis.com", "gemini-1.5-flash"),
  CLAUDE("Claude", "https://api.anthropic.com", "claude-3-5-sonnet-latest"),
  OPENAI("OpenAI", "https://api.openai.com", "gpt-4o-mini"),
  DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-chat");

  private final String label;
  private final String defaultBaseUrl;
  private final String defaultModel;

  AiProvider(String label, String defaultBaseUrl, String defaultModel) {
    this.label = label;
    this.defaultBaseUrl = defaultBaseUrl;
    this.defaultModel = defaultModel;
  }

  public String getLabel() { return label; }
  public String getDefaultBaseUrl() { return defaultBaseUrl; }
  public String getDefaultModel() { return defaultModel; }

  public boolean isOpenAiCompatible() {
    return this == OPENAI || this == DEEPSEEK;
  }
}
