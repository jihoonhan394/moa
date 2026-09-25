package com.moara.moa.web.support;

import com.moara.moa.access.AccessRequestNotFoundException;
import com.moara.moa.ai.AiException;
import com.moara.moa.asset.AssetNotFoundException;
import com.moara.moa.category.CategoryNotFoundException;
import com.moara.moa.connection.ConnectionSessionNotFoundException;
import com.moara.moa.consumable.ConsumableItemNotFoundException;
import com.moara.moa.credential.CredentialNotFoundException;
import com.moara.moa.group.AccessGroupNotFoundException;
import com.moara.moa.inventory.InventoryItemNotFoundException;
import com.moara.moa.notice.NoticeNotFoundException;
import com.moara.moa.onboarding.OnboardingTemplateNotFoundException;
import com.moara.moa.permission.PermissionNotFoundException;
import com.moara.moa.reservation.ReservationNotFoundException;
import com.moara.moa.reservation.SharedResourceNotFoundException;
import com.moara.moa.solution.SolutionNotFoundException;
import com.moara.moa.solution.SolutionSequenceNotFoundException;
import com.moara.moa.user.ManagedUserNotFoundException;
import com.moara.moa.wiki.WikiAttachmentNotFoundException;
import com.moara.moa.wiki.WikiPageNotFoundException;
import com.moara.moa.wiki.WikiSpaceNotFoundException;
import com.moara.moa.wiki.WikiTemplateNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 전역 예외 → 표준 에러 화면 매핑. <b>{@code @ControllerAdvice}여야 모든 컨트롤러에 적용된다</b> —
 * {@code @Controller}로 두면 이 클래스 자신의 예외만 처리되어 사실상 동작하지 않는다.
 *
 * <p>교차 테넌트 접근은 대상의 존재 여부조차 노출하지 않도록 404로 응답한다(403이 아니다).
 * 403은 "그런 자원이 있긴 하다"를 알려 주는 셈이라, ID를 넣어 보며 남의 대장을 훑을 수 있게 된다.
 *
 * <p><b>도메인의 NotFound 예외는 빠짐없이 여기 있어야 한다.</b> 빠지면 그 도메인만 500
 * 화이트라벨 페이지가 나간다 — 목록에서 지운 항목의 오래된 링크를 누르는 흔한 상황에서
 * 사용자는 "시스템이 고장났다"고 읽는다. 실제로 세 개만 등록돼 있어
 * {@code /my/assets/{id}}가 500을 내던 것을 계기로 전 도메인을 채웠다.
 * 새 도메인을 만들 때 이 목록에 추가하는 것을 잊지 말 것.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler({
      AccessGroupNotFoundException.class,
      AccessRequestNotFoundException.class,
      AssetNotFoundException.class,
      CategoryNotFoundException.class,
      ConnectionSessionNotFoundException.class,
      ConsumableItemNotFoundException.class,
      CredentialNotFoundException.class,
      InventoryItemNotFoundException.class,
      ManagedUserNotFoundException.class,
      NoticeNotFoundException.class,
      OnboardingTemplateNotFoundException.class,
      PermissionNotFoundException.class,
      ReservationNotFoundException.class,
      SharedResourceNotFoundException.class,
      SolutionNotFoundException.class,
      SolutionSequenceNotFoundException.class,
      WikiAttachmentNotFoundException.class,
      WikiPageNotFoundException.class,
      WikiSpaceNotFoundException.class,
      WikiTemplateNotFoundException.class})
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String handleNotFound() {
    return "error/404";
  }

  /**
   * AI 호출 실패의 안전망. 지금은 각 화면이 직접 잡아 그 자리에서 안내하지만, 새 AI 기능이
   * 그걸 빠뜨리면 whitelabel 500이 나간다 — 외부 제공자가 잠깐 죽은 것뿐인데 사용자는
   * "시스템이 고장났다"고 읽는다. AI는 <b>거들 뿐</b>이므로 실패해도 그렇게 보여선 안 된다.
   *
   * <p>{@code AiException}의 메시지는 제공자 응답 본문이 아니라 우리가 만든 안내 문구다
   * (본문·URL은 키를 담을 수 있어 담지 않는다). 그대로 화면에 보여도 안전하다.
   */
  @ExceptionHandler(AiException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public String handleAiFailure(AiException exception, Model model) {
    model.addAttribute("reason", exception.getMessage());
    return "error/ai";
  }
}
