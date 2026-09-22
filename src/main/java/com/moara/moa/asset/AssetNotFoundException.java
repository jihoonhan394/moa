package com.moara.moa.asset;

import java.util.UUID;

public class AssetNotFoundException extends RuntimeException {
  public AssetNotFoundException(UUID id) {
    super("Asset was not found: " + id);
  }
}
