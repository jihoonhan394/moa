package com.moara.moa.wiki;

import com.moara.moa.ai.AiException;
import com.moara.moa.ai.AiService;
import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 에디터·뷰어가 비동기로 부르는 위키 AI·렌더 엔드포인트. 전부 {@code @ResponseBody}로 평문/HTML만
 * 돌려주고 화면을 만들지 않는다 — 결과를 어디에 넣을지는 클라이언트가 정한다.
 *
 * <p>여기 있는 것은 모두 <b>상태를 바꾸지 않는다</b>. 문서 저장으로 이어지는 AI 초안 생성은 새 문서
 * 폼을 렌더해야 하므로 {@link WikiPageController}에 있다.
 */
@Controller
public class WikiAiController {
  // AI 프롬프트에 싣는 본문/선택 텍스트 상한(초대형 문서로 인한 비용·타임아웃 방지).
  private static final int MAX_AI_INPUT = 8000;

  private final WikiPageService pageService;
  private final MarkdownService markdownService;
  private final AiService aiService;
  private final AuditLogService auditLogService;
  private final WikiAccessGuard guard;

  public WikiAiController(
      WikiPageService pageService, MarkdownService markdownService, AiService aiService,
      AuditLogService auditLogService, WikiAccessGuard guard) {
    this.pageService = pageService;
    this.markdownService = markdownService;
    this.aiService = aiService;
    this.auditLogService = auditLogService;
    this.guard = guard;
  }

  /**
   * 페이지 AI 요약(Tier 1): 현재 문서 본문을 근거로 3줄 요약을 생성한다. 열람 권한만 있으면 사용 가능하며,
   * 문서에 없는 내용은 지어내지 않도록 지시한다. 비동기 호출용으로 요약 텍스트만 평문 반환한다.
   */
  @PostMapping("/wiki/{id}/summary")
  @ResponseBody
  public String summary(@PathVariable UUID id) {
    UUID tenantId = guard.tenantId();
    WikiPage wikiPage = pageService.findById(tenantId, id);
    guard.requireView(wikiPage.getSpaceId());
    if (!aiService.isConfigured(tenantId)) {
      return "AI가 설정되지 않았습니다. 기관 관리자가 'AI 설정'에서 제공자·키를 등록하세요.";
    }
    String prompt = "다음 사내 위키 문서를 한국어로 3줄 이내, 핵심만 간결히 요약해줘."
        + " 문서에 없는 내용은 지어내지 말 것.\n\n제목: " + wikiPage.getTitle()
        + "\n\n본문:\n" + clip(wikiPage.getContent(), MAX_AI_INPUT);
    try {
      String result = aiService.generate(tenantId, prompt);
      audit("WIKI_AI_SUMMARY", id, null);
      return result;
    } catch (AiException exception) {
      return "요약 생성에 실패했습니다: " + exception.getMessage();
    }
  }

  /**
   * 에디터 라이브 미리보기: 입력한 마크다운을 저장 시와 <b>동일한 렌더+새니타이즈 파이프라인</b>으로 처리해
   * 안전 HTML을 반환한다. 미리보기와 실제 저장 결과가 항상 일치하고, XSS 방어도 한 곳에서 보장된다.
   */
  @PostMapping("/wiki/preview")
  @ResponseBody
  public String preview(@RequestParam(required = false) String content) {
    return markdownService.toSafeHtml(content);
  }

  /**
   * 인라인 저작 AI(Tier 2): 에디터에서 선택한 텍스트(또는 본문)를 요약/개선/번역/이어쓰기 한다.
   * 결과 텍스트만 평문 반환하고, 삽입·치환은 클라이언트가 한다. 문서 저장이 아니라 작성 보조이므로 상태 변경 없음.
   */
  @PostMapping("/wiki/ai-assist")
  @ResponseBody
  public String aiAssist(
      @RequestParam String action, @RequestParam(required = false) String text) {
    UUID tenantId = guard.tenantId();
    if (!aiService.isConfigured(tenantId)) {
      return "AI가 설정되지 않았습니다. 기관 관리자가 'AI 설정'에서 제공자·키를 등록하세요.";
    }
    String input = text == null ? "" : text;
    if (input.isBlank()) {
      return "(내용을 입력하거나 텍스트를 선택한 뒤 사용하세요.)";
    }
    input = clip(input, MAX_AI_INPUT);
    String prompt = switch (action) {
      case "summarize" -> "다음 텍스트를 한국어로 핵심만 간결히 요약해줘. 요약 결과만 출력:\n\n" + input;
      case "improve" -> "다음 텍스트의 문장을 자연스럽게 다듬고 맞춤법을 교정해줘. 의미는 유지하고 결과 문장만 출력:\n\n" + input;
      case "translate" -> "다음 텍스트가 한국어면 영어로, 영어면 한국어로 번역해줘. 번역 결과만 출력:\n\n" + input;
      case "continue" -> "다음 글에 자연스럽게 이어질 다음 문단을 한국어로 작성해줘. 이어질 문단만 출력:\n\n" + input;
      default -> null;
    };
    if (prompt == null) {
      return "(지원하지 않는 작업입니다.)";
    }
    try {
      return aiService.generate(tenantId, prompt);
    } catch (AiException exception) {
      return "AI 처리에 실패했습니다: " + exception.getMessage();
    }
  }

  /** AI 프롬프트에 실을 텍스트를 상한 길이로 자른다(초과 시 생략 표시). */
  private static String clip(String text, int max) {
    if (text == null) {
      return "";
    }
    return text.length() <= max ? text : text.substring(0, max) + " …(이하 생략)";
  }

  private void audit(String action, UUID targetId, String message) {
    UUID actorId = guard.userId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          guard.tenantId(), actorId, action, "Wiki", targetId, AuditResult.SUCCESS, message);
    }
  }
}
