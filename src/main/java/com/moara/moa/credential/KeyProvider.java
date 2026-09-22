package com.moara.moa.credential;

import javax.crypto.SecretKey;

/**
 * 봉투암호화의 KEK(마스터 키) 공급자. 구현을 교체해 키 보관 위치를 바꾼다
 * (v1: env/KMS, 이후: HashiCorp Vault Transit 등). KEK는 DB 밖에 둔다.
 */
public interface KeyProvider {
  /** 현재 KEK. */
  SecretKey masterKey();

  /** 특정 버전 KEK(로테이션 대비). v1은 현재 버전만 지원. */
  SecretKey masterKey(int version);

  /** 현재 KEK 버전. */
  int currentKeyVersion();
}
