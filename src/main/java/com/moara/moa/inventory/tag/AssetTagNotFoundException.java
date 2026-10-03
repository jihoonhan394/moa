package com.moara.moa.inventory.tag;

/**
 * 그런 코드가 없다.
 *
 * <p>다른 기관의 코드를 찍은 경우도 이것으로 떨어뜨린다. "다른 기관 것입니다"라고 알려 주면
 * 그 코드가 실재한다는 사실이 새어 나간다(서버 AGENTS.md: 크로스 테넌트는 404).
 */
public class AssetTagNotFoundException extends RuntimeException {
  public AssetTagNotFoundException(String code) {
    super("관리번호를 찾을 수 없습니다: " + code);
  }
}
