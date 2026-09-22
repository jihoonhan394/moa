package com.moara.moa.credential;

/** 봉투암호화 결과. 평문은 담지 않는다. */
public record EncryptedSecret(String secretCiphertext, String dekWrapped, int keyVersion) {}
