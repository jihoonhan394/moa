package com.moara.moa.security;

/** RFC 4648 Base32(대문자, 패딩 없음). Google Authenticator 시크릿 인코딩용 — 최소 구현. */
final class Base32 {
  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

  private Base32() {}

  static String encode(byte[] data) {
    StringBuilder out = new StringBuilder();
    int buffer = 0;
    int bits = 0;
    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xff);
      bits += 8;
      while (bits >= 5) {
        out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1f));
        bits -= 5;
      }
    }
    if (bits > 0) {
      out.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1f));
    }
    return out.toString();
  }

  static byte[] decode(String encoded) {
    String s = encoded.trim().replace(" ", "").replace("=", "").toUpperCase();
    int buffer = 0;
    int bits = 0;
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    for (int i = 0; i < s.length(); i++) {
      int val = ALPHABET.indexOf(s.charAt(i));
      if (val < 0) {
        throw new IllegalArgumentException("Invalid Base32 character");
      }
      buffer = (buffer << 5) | val;
      bits += 5;
      if (bits >= 8) {
        out.write((buffer >> (bits - 8)) & 0xff);
        bits -= 8;
      }
    }
    return out.toByteArray();
  }
}
