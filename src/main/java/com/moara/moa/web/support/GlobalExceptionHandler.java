package com.moara.moa.web.support;

import com.moara.moa.asset.AssetNotFoundException;
import com.moara.moa.notice.NoticeNotFoundException;
import com.moara.moa.user.ManagedUserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 전역 예외 → 표준 에러 화면 매핑. <b>{@code @ControllerAdvice}여야 모든 컨트롤러에 적용된다</b> —
 * {@code @Controller}로 두면 이 클래스 자신의 예외만 처리되어 사실상 동작하지 않는다.
 *
 * <p>교차 테넌트 접근은 대상의 존재 여부조차 노출하지 않도록 404로 응답한다(403이 아니다).
 */
@ControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler({
      AssetNotFoundException.class, ManagedUserNotFoundException.class, NoticeNotFoundException.class})
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String handleNotFound() {
    return "error/404";
  }
}
