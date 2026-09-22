package com.moara.moa.expiration;

import com.moara.moa.ai.AiService;
import com.moara.moa.inventory.InventoryItem;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.solution.ManagedSolution;
import com.moara.moa.solution.SolutionAccessService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료 항목의 <b>영향 사실</b>을 모아(누가/무엇이 영향받는지) AI에게 갱신·후속조치 초안을 요청한다.
 * 영향 사실 수집({@link #buildContext})은 AI 없이 순수하게 동작하므로 단독 테스트가 가능하다.
 * "누가 영향받는가"는 접근·배정을 함께 아는 MOA만 계산할 수 있다(포인트 자산관리 SW는 못 함).
 */
@Service
@Transactional(readOnly = true)
public class ExpirationImpactService {
  private final InventoryItemService inventoryService;
  private final SolutionAccessService solutionAccessService;
  private final ManagedUserService userService;
  private final AiService aiService;

  public ExpirationImpactService(
      InventoryItemService inventoryService, SolutionAccessService solutionAccessService,
      ManagedUserService userService, AiService aiService) {
    this.inventoryService = inventoryService;
    this.solutionAccessService = solutionAccessService;
    this.userService = userService;
    this.aiService = aiService;
  }

  /** 영향 사실 묶음(제목 + 사실 목록). AI 프롬프트의 근거가 된다. */
  public record ImpactContext(String title, List<String> facts) {}

  public ImpactContext buildContext(
      UUID tenantId, ExpirationSourceType sourceType, UUID sourceId, String label, String detail) {
    List<String> facts = new ArrayList<>();
    if (detail != null && !detail.isBlank()) {
      facts.add("상세: " + detail);
    }
    if (sourceType == null) {
      return new ImpactContext(label, facts);
    }
    switch (sourceType) {
      case INVENTORY -> inventoryFacts(tenantId, sourceId, facts);
      case ACCESS_GRANT -> accessGrantFacts(tenantId, sourceId, facts);
      case SUBSCRIPTION -> subscriptionFacts(tenantId, facts);
      default -> { /* 사실 없음 */ }
    }
    return new ImpactContext(label, facts);
  }

  private void inventoryFacts(UUID tenantId, UUID itemId, List<String> facts) {
    if (itemId == null) {
      return;
    }
    try {
      InventoryItem item = inventoryService.findById(tenantId, itemId);
      facts.add("품목: " + item.getName() + " (" + item.getType().getLabel() + ")");
      if (item.getAssignedUserId() == null) {
        facts.add("보유자: 미배정 (즉시 재배정 시 영향 없음)");
        return;
      }
      String holder = userName(item.getAssignedUserId());
      facts.add("현재 보유자: " + holder);
      List<ManagedSolution> solutions =
          solutionAccessService.assignedSolutions(tenantId, item.getAssignedUserId());
      if (!solutions.isEmpty()) {
        String names = solutions.stream().map(ManagedSolution::getName).reduce((a, b) -> a + ", " + b).orElse("");
        facts.add("보유자가 배정받은 솔루션 " + solutions.size() + "개: " + names
            + " (자산 회수 시 업무 연속성 확인 필요)");
      }
    } catch (RuntimeException exception) {
      // 삭제된 항목 등은 사실 없이 진행.
    }
  }

  private void accessGrantFacts(UUID tenantId, UUID userId, List<String> facts) {
    if (userId == null) {
      return;
    }
    facts.add("영향 대상: " + userName(userId));
    facts.add("만료 시 이 사용자가 해당 접근 권한을 잃습니다(업무 지속 필요 시 갱신).");
  }

  private void subscriptionFacts(UUID tenantId, List<String> facts) {
    int activeUsers = userService.activeUserEmails(tenantId).size();
    facts.add("영향 범위: 기관 전체 (활성 사용자 약 " + activeUsers + "명)");
    facts.add("만료 시 로그인/서비스 접근이 중단될 수 있습니다.");
  }

  /**
   * 만료 항목에 대한 영향분석·후속조치 초안을 AI로 생성한다. 주어진 사실만 사용하도록 지시해
   * 환각(허구 수치)을 억제한다. AI 미설정/오류는 상위에서 안내로 처리한다.
   */
  public String draft(
      UUID tenantId, ExpirationSourceType sourceType, UUID sourceId, String category, String label,
      LocalDate expiresOn, long daysLeft) {
    ImpactContext context = buildContext(tenantId, sourceType, sourceId, label, null);
    StringBuilder prompt = new StringBuilder();
    prompt.append("당신은 사내 IT·자산 운영 담당자를 돕는 어시스턴트입니다. ")
        .append("아래 '만료 예정 항목'에 대해 한국어로 간결하게 작성하세요:\n")
        .append("1) 영향 범위 요약(주어진 사실만 근거로, 수치를 지어내지 말 것)\n")
        .append("2) 갱신/후속조치 체크리스트(담당자가 순서대로 할 일)\n")
        .append("3) 놓치면 위험한 점 한두 가지\n\n")
        .append("[항목] ").append(label).append('\n')
        .append("[분류] ").append(category).append('\n')
        .append("[만료일] ").append(expiresOn)
        .append(daysLeft < 0 ? " (이미 " + (-daysLeft) + "일 지남)" : " (D-" + daysLeft + ")").append('\n')
        .append("[확인된 사실]\n");
    if (context.facts().isEmpty()) {
      prompt.append("- (추가 사실 없음)\n");
    } else {
      for (String fact : context.facts()) {
        prompt.append("- ").append(fact).append('\n');
      }
    }
    return aiService.generate(tenantId, prompt.toString());
  }

  public boolean aiConfigured(UUID tenantId) {
    return aiService.isConfigured(tenantId);
  }

  private String userName(UUID userId) {
    try {
      ManagedUser user = userService.findById(userId);
      return user.getName();
    } catch (RuntimeException exception) {
      return "(알 수 없음)";
    }
  }
}
