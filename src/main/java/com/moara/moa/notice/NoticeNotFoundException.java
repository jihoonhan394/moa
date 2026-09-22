package com.moara.moa.notice;

import java.util.UUID;

/** 존재하지 않거나 다른 기관 소속인 공지를 조회할 때. */
public class NoticeNotFoundException extends RuntimeException {
  public NoticeNotFoundException(UUID id) {
    super("Notice not found: " + id);
  }
}
