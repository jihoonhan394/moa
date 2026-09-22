package com.moara.moa.ai;

import com.moara.moa.wiki.WikiAccessService;
import com.moara.moa.wiki.WikiPage;
import com.moara.moa.wiki.WikiPageService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사내 지식봇: 위키 문서를 근거로 자연어 질문에 답한다. <b>반드시 질문자의 위키 공간 열람 권한을 존중</b>해
 * 권한 없는 문서가 봇을 통해 새지 않도록 한다(공간 ACL 우회 금지). 근거 문서 선별({@link #relevantPages})은
 * AI 없이 동작하므로 단독 테스트가 가능하다.
 */
@Service
@Transactional(readOnly = true)
public class KnowledgeService {
  private static final int MAX_PAGES = 6;
  private static final int MAX_EXCERPT = 1200;

  private final WikiPageService pageService;
  private final WikiAccessService accessService;
  private final AiService aiService;

  public KnowledgeService(
      WikiPageService pageService, WikiAccessService accessService, AiService aiService) {
    this.pageService = pageService;
    this.accessService = accessService;
    this.aiService = aiService;
  }

  /** 지식봇 답변(근거 문서 = 클릭 가능한 출처). */
  public record Answer(String text, List<SourceRef> sources) {}

  /** 근거 문서 출처: 화면에서 /wiki/{pageId} 링크로 렌더한다. */
  public record SourceRef(UUID pageId, String title) {}

  /**
   * 질문과 관련된, <b>질문자가 열람 가능한</b> 위키 문서를 관련도순으로 최대 {@value #MAX_PAGES}건 고른다.
   * 관련도는 질문 키워드가 제목/본문에 얼마나 겹치는지로 매긴다(임베딩 없는 단순 매칭).
   */
  public List<WikiPage> relevantPages(UUID tenantId, UUID userId, boolean tenantAdmin, String question) {
    List<String> keywords = keywords(question);
    List<WikiPage> viewable = pageService.findAll(tenantId).stream()
        .filter(page -> accessService.canView(tenantId, userId, tenantAdmin, page.getSpaceId()))
        .toList();
    return viewable.stream()
        .map(page -> new Scored(page, score(page, keywords)))
        .filter(scored -> scored.score > 0)
        .sorted(Comparator.comparingInt((Scored s) -> s.score).reversed())
        .limit(MAX_PAGES)
        .map(scored -> scored.page)
        .toList();
  }

  /**
   * 질문에 답한다. 근거 문서 발췌만 사용하도록 지시해 환각을 억제하고, 문서가 없으면 모른다고 답하게 한다.
   * AI 미설정/오류는 상위에서 안내로 처리한다.
   */
  public Answer answer(UUID tenantId, UUID userId, boolean tenantAdmin, String question) {
    List<WikiPage> pages = relevantPages(tenantId, userId, tenantAdmin, question);
    StringBuilder prompt = new StringBuilder();
    prompt.append("당신은 회사 내부 지식 도우미입니다. 아래 '사내 문서 발췌'만 근거로 질문에 한국어로 답하세요. ")
        .append("발췌에 없는 내용은 지어내지 말고 '사내 문서에서 찾지 못했습니다'라고 답하세요.\n\n")
        .append("[질문]\n").append(question).append("\n\n")
        .append("[사내 문서 발췌]\n");
    if (pages.isEmpty()) {
      prompt.append("(질문과 관련된, 열람 가능한 문서가 없습니다)\n");
    } else {
      for (WikiPage page : pages) {
        prompt.append("## ").append(page.getTitle()).append('\n')
            .append(excerpt(page.getContent())).append("\n\n");
      }
    }
    String text = aiService.generate(tenantId, prompt.toString());
    return new Answer(text, pages.stream().map(p -> new SourceRef(p.getId(), p.getTitle())).toList());
  }

  public boolean aiConfigured(UUID tenantId) {
    return aiService.isConfigured(tenantId);
  }

  private int score(WikiPage page, List<String> keywords) {
    String haystack = ((page.getTitle() == null ? "" : page.getTitle()) + " "
        + (page.getContent() == null ? "" : page.getContent())).toLowerCase();
    String title = page.getTitle() == null ? "" : page.getTitle().toLowerCase();
    int score = 0;
    for (String keyword : keywords) {
      if (title.contains(keyword)) {
        score += 3; // 제목 일치 가중
      } else if (haystack.contains(keyword)) {
        score += 1;
      }
    }
    return score;
  }

  private List<String> keywords(String question) {
    if (question == null) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String token : Arrays.asList(question.toLowerCase().split("[\\s,\\.\\?!]+"))) {
      if (token.length() >= 2 && !out.contains(token)) {
        out.add(token);
      }
    }
    return out;
  }

  private String excerpt(String content) {
    if (content == null) {
      return "";
    }
    return content.length() <= MAX_EXCERPT ? content : content.substring(0, MAX_EXCERPT) + " …";
  }

  private record Scored(WikiPage page, int score) {}
}
