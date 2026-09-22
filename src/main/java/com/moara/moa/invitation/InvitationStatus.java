package com.moara.moa.invitation;

/** 초대 상태. PENDING=발송·대기, ACCEPTED=수락(가입 완료), REVOKED=철회. 만료는 expiresAt로 판정. */
public enum InvitationStatus {
  PENDING, ACCEPTED, REVOKED
}
