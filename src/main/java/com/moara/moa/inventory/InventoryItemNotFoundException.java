package com.moara.moa.inventory;

import java.util.UUID;

public class InventoryItemNotFoundException extends RuntimeException {
  public InventoryItemNotFoundException(UUID id) {
    super("Inventory item was not found: " + id);
  }
}
