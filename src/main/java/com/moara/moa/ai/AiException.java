package com.moara.moa.ai;

/** AI 호출 실패(설정/네트워크/제공자 오류). 메시지에 API 키를 절대 담지 않는다. */
public class AiException extends RuntimeException {
  public AiException(String message) {
    super(message);
  }
}
