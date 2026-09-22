package com.moara.moa.wiki;

import java.util.UUID;

public class WikiTemplateNotFoundException extends RuntimeException {
  public WikiTemplateNotFoundException(UUID id) {
    super("Wiki template was not found: " + id);
  }
}
