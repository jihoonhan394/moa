package com.moara.moa.wiki;

import java.util.UUID;

/** 요청한 첨부가 없거나(다른 테넌트 포함) 파일 본체가 사라진 경우. */
public class WikiAttachmentNotFoundException extends RuntimeException {
  public WikiAttachmentNotFoundException(UUID id) {
    super("첨부를 찾을 수 없습니다: " + id);
  }
}
