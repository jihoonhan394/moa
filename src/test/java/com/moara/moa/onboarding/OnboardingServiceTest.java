package com.moara.moa.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import com.moara.moa.wiki.WikiSpace;
import com.moara.moa.wiki.WikiSpaceForm;
import com.moara.moa.wiki.WikiSpaceService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 온보딩 템플릿 적용 → 위키 공간 즉시 부여 + 할 일/필독 체크리스트 생성, 완료율, 퇴사 시 정리를 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class OnboardingServiceTest {
  @Autowired private OnboardingService onboardingService;
  @Autowired private WikiSpaceService wikiSpaceService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void applyingTemplateProvisionsWikiAndCreatesChecklist() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("입사사", "ONB" + System.nanoTime()));
    ManagedUser hire = newUser(tenant.getId());
    WikiSpace space = wikiSpaceService.create(tenant.getId(), null, new WikiSpaceForm("영업팀", "영업 문서"));

    OnboardingTemplate template = onboardingService.createTemplate(tenant.getId(), "영업팀");
    onboardingService.addItem(tenant.getId(), template.getId(),
        OnboardingItemType.GRANT_WIKI_SPACE, space.getId(), null);
    onboardingService.addItem(tenant.getId(), template.getId(),
        OnboardingItemType.TASK, null, "VPN 설치");
    onboardingService.addItem(tenant.getId(), template.getId(),
        OnboardingItemType.ACK_DOC, null, "보안 서약 동의");

    onboardingService.apply(tenant.getId(), hire.getId(), template.getId());

    // 위키 공간이 개인에게 부여되어 '내 공간'으로 조회된다.
    assertThat(wikiSpaceService.findGrantedToUser(tenant.getId(), hire.getId()))
        .extracting(WikiSpace::getId).contains(space.getId());
    // 체크리스트는 TASK/ACK_DOC 2건, 완료율 0%.
    assertThat(onboardingService.myTasks(tenant.getId(), hire.getId())).hasSize(2);
    assertThat(onboardingService.progressPercent(tenant.getId(), hire.getId())).isZero();

    // 한 건 완료 → 50%.
    UUID firstTask = onboardingService.myTasks(tenant.getId(), hire.getId()).get(0).getId();
    onboardingService.markDone(tenant.getId(), hire.getId(), firstTask);
    assertThat(onboardingService.progressPercent(tenant.getId(), hire.getId())).isEqualTo(50);

    // 퇴사 회수 → 체크리스트 정리.
    assertThat(onboardingService.removeTasksOf(tenant.getId(), hire.getId())).isEqualTo(2);
    assertThat(onboardingService.myTasks(tenant.getId(), hire.getId())).isEmpty();
  }

  private ManagedUser newUser(UUID tenantId) {
    String username = "u" + System.nanoTime();
    return userService.create(tenantId,
        new UserForm(username, "신입", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
