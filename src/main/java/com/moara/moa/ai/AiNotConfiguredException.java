package com.moara.moa.ai;

/** 기관 AI 설정이 없거나 비활성/키 미설정. */
public class AiNotConfiguredException extends AiException {
  public AiNotConfiguredException() {
    super("AI가 설정되지 않았습니다. 기관 관리자 → AI 설정에서 제공자와 API 키를 등록하세요.");
  }
}
