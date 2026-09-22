package com.moara.moa.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import com.moara.moa.wiki.WikiPage;
import com.moara.moa.wiki.WikiPageForm;
import com.moara.moa.wiki.WikiPageService;
import com.moara.moa.wiki.WikiSpace;
import com.moara.moa.wiki.WikiSpaceForm;
import com.moara.moa.wiki.WikiSpaceService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 지식봇의 근거 문서 선별을 AI 호출 없이 검증한다: (1) 열람 권한 없는 문서는 제외(공간 ACL 우회 금지),
 * (2) 질문 키워드가 맞는 문서만 근거로 삼는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class KnowledgeServiceTest {
  @Autowired private KnowledgeService knowledgeService;
  @Autowired private WikiSpaceService spaceService;
  @Autowired private WikiPageService pageService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void groundsOnPermittedMatchingDocsOnly() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("지식사", "KB" + System.nanoTime()));
    ManagedUser outsider = newUser(tenant.getId());
    WikiSpace space = spaceService.create(tenant.getId(), null, new WikiSpaceForm("인프라", "운영"));
    pageService.create(tenant.getId(), space.getId(), outsider.getId(),
        new WikiPageForm("백업 서버 정책", "백업 서버는 매일 새벽 3시에 스냅샷을 찍는다."));

    // 기관 관리자(tenantAdmin=true)는 모든 공간 열람 → 키워드 맞는 문서를 근거로 얻는다.
    var adminPages = knowledgeService.relevantPages(tenant.getId(), UUID.randomUUID(), true, "백업 서버 정책");
    assertThat(adminPages).extracting(WikiPage::getTitle).contains("백업 서버 정책");

    // 권한 없는 일반 사용자(tenantAdmin=false, 공간 권한 미부여)는 같은 문서를 근거로 얻지 못한다.
    var outsiderPages = knowledgeService.relevantPages(tenant.getId(), outsider.getId(), false, "백업 서버 정책");
    assertThat(outsiderPages).isEmpty();

    // 키워드가 전혀 안 맞으면 관리자도 근거 문서 없음.
    var noMatch = knowledgeService.relevantPages(tenant.getId(), UUID.randomUUID(), true, "회의실 예약 방법");
    assertThat(noMatch).isEmpty();
  }

  private ManagedUser newUser(UUID tenantId) {
    String username = "u" + System.nanoTime();
    return userService.create(tenantId,
        new UserForm(username, "사원", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
