package com.moara.moa.inventory.tag;

/** 이미 자산에 붙은 스티커를 다시 붙이려 할 때. */
public class AssetTagAlreadyAttachedException extends RuntimeException {
  public AssetTagAlreadyAttachedException(String code) {
    super("이미 사용 중인 관리번호입니다: " + code);
  }
}
