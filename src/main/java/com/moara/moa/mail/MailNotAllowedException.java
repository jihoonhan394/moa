package com.moara.moa.mail;

/**
 * SMTP 호스트가 허용되지 않는 대역(루프백/링크로컬/사설망 등)일 때 발생한다.
 * 기관 관리자가 SMTP host/port를 내부망 주소로 지정해 내부 자원을 탐침(SSRF)하는 것을 차단한다.
 * 메시지는 사용자에게 그대로 노출해도 안전한 일반 안내만 담는다(연결 결과 오라클 노출 금지).
 */
public class MailNotAllowedException extends RuntimeException {
  public MailNotAllowedException(String message) {
    super(message);
  }
}
