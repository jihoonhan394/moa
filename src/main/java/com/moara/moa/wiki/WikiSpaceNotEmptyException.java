package com.moara.moa.wiki;

import java.util.UUID;

/** 페이지가 있는 공간은 삭제할 수 없다. */
public class WikiSpaceNotEmptyException extends RuntimeException {
  public WikiSpaceNotEmptyException(UUID id) {
    super("Wiki space has pages and cannot be deleted: " + id);
  }
}
