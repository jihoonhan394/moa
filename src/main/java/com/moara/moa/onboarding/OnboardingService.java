package com.moara.moa.onboarding;

import com.moara.moa.notification.NotificationService;
import com.moara.moa.solution.SolutionAccessService;
import com.moara.moa.wiki.WikiAccessLevel;
import com.moara.moa.wiki.WikiSpaceService;
import com.moara.moa.wiki.WikiSubjectType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입사 온보딩: 부서/직무 템플릿 관리와 신입에 대한 일괄 프로비저닝. 퇴사 회수(OffboardHandler)의 거울로,
 * 템플릿 적용 시 솔루션·위키 공간을 즉시 부여하고 할 일/필독문서를 개인 체크리스트로 만든다.
 * 물리 자산(노트북·차량 등 개체)은 여기서 다루지 않는다 — 경영지원팀이 인벤토리에서 개체를 배정한다.
 */
@Service
@Transactional(readOnly = true)
public class OnboardingService {
  private final OnboardingTemplateRepository templateRepository;
  private final OnboardingTemplateItemRepository itemRepository;
  private final OnboardingTaskRepository taskRepository;
  private final SolutionAccessService solutionAccessService;
  private final WikiSpaceService wikiSpaceService;
  private final NotificationService notificationService;

  public OnboardingService(
      OnboardingTemplateRepository templateRepository,
      OnboardingTemplateItemRepository itemRepository,
      OnboardingTaskRepository taskRepository,
      SolutionAccessService solutionAccessService,
      WikiSpaceService wikiSpaceService,
      NotificationService notificationService) {
    this.templateRepository = templateRepository;
    this.itemRepository = itemRepository;
    this.taskRepository = taskRepository;
    this.solutionAccessService = solutionAccessService;
    this.wikiSpaceService = wikiSpaceService;
    this.notificationService = notificationService;
  }

  // --- 템플릿 관리(기관 관리자) ---

  public List<OnboardingTemplate> templates(UUID tenantId) {
    return templateRepository.findAllByTenantIdOrderByNameAsc(tenantId);
  }

  public OnboardingTemplate template(UUID tenantId, UUID templateId) {
    return templateRepository.findByTenantIdAndId(tenantId, templateId)
        .orElseThrow(() -> new OnboardingTemplateNotFoundException(templateId));
  }

  public List<OnboardingTemplateItem> items(UUID templateId) {
    return itemRepository.findAllByTemplateIdOrderBySortOrderAsc(templateId);
  }

  @Transactional
  public OnboardingTemplate createTemplate(UUID tenantId, String name) {
    return templateRepository.save(new OnboardingTemplate(UUID.randomUUID(), tenantId, name, OffsetDateTime.now()));
  }

  @Transactional
  public void addItem(UUID tenantId, UUID templateId, OnboardingItemType type, UUID refId, String label) {
    template(tenantId, templateId); // 소유 검증
    int nextSort = items(templateId).size();
    itemRepository.save(new OnboardingTemplateItem(
        UUID.randomUUID(), templateId, type, refId, label, nextSort));
  }

  @Transactional
  public void removeItem(UUID tenantId, UUID templateId, UUID itemId) {
    template(tenantId, templateId); // 소유 검증
    itemRepository.findById(itemId)
        .filter(item -> item.getTemplateId().equals(templateId))
        .ifPresent(itemRepository::delete);
  }

  @Transactional
  public void deleteTemplate(UUID tenantId, UUID templateId) {
    OnboardingTemplate template = template(tenantId, templateId);
    itemRepository.deleteByTemplateId(templateId);
    templateRepository.delete(template);
  }

  // --- 적용(신입에게 일괄 프로비저닝) ---

  /**
   * 템플릿을 신입에게 적용한다. 솔루션·위키 공간은 즉시 부여하고, 할 일/필독문서는 개인 체크리스트로 만든다.
   * 개별 항목 실패가 전체를 막지 않도록 각 항목을 독립 처리한다(부분 성공 허용, 멱등적).
   */
  @Transactional
  public void apply(UUID tenantId, UUID userId, UUID templateId) {
    OnboardingTemplate template = template(tenantId, templateId);
    OffsetDateTime now = OffsetDateTime.now();
    for (OnboardingTemplateItem item : items(templateId)) {
      switch (item.getItemType()) {
        case ASSIGN_SOLUTION -> {
          if (item.getRefId() != null) {
            trySilently(() -> solutionAccessService.assignUser(tenantId, item.getRefId(), userId));
          }
        }
        case GRANT_WIKI_SPACE -> {
          if (item.getRefId() != null) {
            trySilently(() -> wikiSpaceService.grant(
                tenantId, item.getRefId(), WikiSubjectType.USER, userId, WikiAccessLevel.VIEW));
          }
        }
        case TASK, ACK_DOC -> taskRepository.save(new OnboardingTask(
            UUID.randomUUID(), tenantId, userId, item.getItemType(),
            item.getLabel() == null ? item.getItemType().getLabel() : item.getLabel(),
            item.getRefId(), now));
      }
    }
    notificationService.notify(tenantId, userId,
        "환영합니다! 온보딩이 시작되었습니다",
        "'" + template.getName() + "' 온보딩이 배정되었습니다. 내 워크스페이스에서 할 일과 배정 자원을 확인하세요.",
        "/my/workspace");
  }

  // --- 신입 개인(체크리스트) ---

  public List<OnboardingTask> myTasks(UUID tenantId, UUID userId) {
    return taskRepository.findAllByTenantIdAndUserIdOrderByCreatedAtAsc(tenantId, userId);
  }

  @Transactional
  public void markDone(UUID tenantId, UUID userId, UUID taskId) {
    taskRepository.findByTenantIdAndIdAndUserId(tenantId, taskId, userId)
        .ifPresent(task -> task.complete(OffsetDateTime.now()));
  }

  /** 온보딩 완료율(%). 체크리스트가 없으면 0. */
  public int progressPercent(UUID tenantId, UUID userId) {
    long total = taskRepository.countByTenantIdAndUserId(tenantId, userId);
    if (total == 0) {
      return 0;
    }
    long done = taskRepository.countByTenantIdAndUserIdAndDoneTrue(tenantId, userId);
    return (int) Math.round(done * 100.0 / total);
  }

  /** 퇴사 회수: 이 사용자의 온보딩 체크리스트 전부 삭제. */
  @Transactional
  public long removeTasksOf(UUID tenantId, UUID userId) {
    return taskRepository.deleteByTenantIdAndUserId(tenantId, userId);
  }

  private void trySilently(Runnable action) {
    try {
      action.run();
    } catch (RuntimeException exception) {
      // 개별 항목(삭제된 솔루션/공간 등) 실패는 온보딩 전체를 막지 않는다.
    }
  }
}
