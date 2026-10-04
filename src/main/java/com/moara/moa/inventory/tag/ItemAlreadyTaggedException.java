package com.moara.moa.inventory.tag;

/**
 * 그 자산에는 이미 관리번호가 붙어 있다.
 *
 * <p>현장에서 두 사람이 각자 스티커를 들고 같은 장비에 붙이면 생긴다. 막지 않으면 DB 유니크
 * 제약이 터져 500 이 나가고, 사용자는 무엇이 잘못됐는지 알 수 없다.
 */
public class ItemAlreadyTaggedException extends RuntimeException {

  private final String existingCode;

  public ItemAlreadyTaggedException(String existingCode) {
    super("이미 관리번호가 붙어 있는 자산입니다: " + existingCode);
    this.existingCode = existingCode;
  }

  public String getExistingCode() {
    return existingCode;
  }
}
