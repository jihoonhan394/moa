package com.moara.moa.invitation;

/** 초대 토큰이 유효하지 않음(없음/만료/이미 수락·철회). 존재·상태를 노출하지 않는다. */
public class InvitationInvalidException extends RuntimeException {
  public InvitationInvalidException() {
    super("Invalid or expired invitation");
  }
}
