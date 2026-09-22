package com.moara.moa.wiki;

import java.util.UUID;

public class WikiPageNotFoundException extends RuntimeException {
  public WikiPageNotFoundException(UUID id) {
    super("Wiki page was not found: " + id);
  }
}
