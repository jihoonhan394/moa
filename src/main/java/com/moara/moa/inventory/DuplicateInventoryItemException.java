package com.moara.moa.inventory;

public class DuplicateInventoryItemException extends RuntimeException {
  public DuplicateInventoryItemException(String name) {
    super("Inventory item name already exists in tenant: " + name);
  }
}
