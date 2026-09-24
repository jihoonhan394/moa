package com.moara.moa.ai;

import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.UserRole;
import com.moara.moa.wiki.MarkdownService;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 사내 지식봇 — 위키 문서를 근거로 질문에 답한다. 답변은 <b>질문자가 열람 가능한</b> 문서만 근거로 하므로
 * 공간 권한을 우회하지 않는다. 모든 인증 사용자가 사용할 수 있다(각자 볼 수 있는 범위 안에서만 답을 받음).
 */
@Controller
public class AssistantController {
  private final KnowledgeService knowledgeService;
  private final MarkdownService markdownService;
  private final TenantContext tenantContext;

  public AssistantController(
      KnowledgeService knowledgeService, MarkdownService markdownService, TenantContext tenantContext) {
    this.knowledgeService = knowledgeService;
    this.markdownService = markdownService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/assistant")
  public String index(Model model) {
    model.addAttribute("aiConfigured", knowledgeService.aiConfigured(tenantContext.currentTenantId()));
    model.addAttribute("page", "assistant");
    return "assistant";
  }

  @PostMapping("/assistant")
  public String ask(@RequestParam String question, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    model.addAttribute("question", question);
    model.addAttribute("aiConfigured", knowledgeService.aiConfigured(tenantId));
    model.addAttribute("page", "assistant");
    if (!knowledgeService.aiConfigured(tenantId)) {
      model.addAttribute("error", "AI가 설정되지 않았습니다. 기관 관리자가 'AI 설정'에서 제공자와 API 키를 등록하세요.");
      return "assistant";
    }
    if (question == null || question.isBlank()) {
      return "assistant";
    }
    try {
      KnowledgeService.Answer answer = knowledgeService.answer(
          tenantId, tenantContext.currentUserId(), isTenantAdmin(), question);
      model.addAttribute("answer", answer.text());
      model.addAttribute("answerHtml", markdownService.toSafeHtml(answer.text()));
      model.addAttribute("sources", answer.sources());
    } catch (RuntimeException exception) {
      model.addAttribute("error", "답변 생성에 실패했습니다: " + exception.getMessage());
    }
    return "assistant";
  }

  private boolean isTenantAdmin() {
    MoaUserDetails user = tenantContext.currentUser();
    return user != null && user.hasRole(UserRole.TENANT_ADMIN);
  }
}
