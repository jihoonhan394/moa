package com.moara.moa.wiki;

import java.util.UUID;

public class WikiSpaceNotFoundException extends RuntimeException {
  public WikiSpaceNotFoundException(UUID id) {
    super("Wiki space was not found: " + id);
  }
}
