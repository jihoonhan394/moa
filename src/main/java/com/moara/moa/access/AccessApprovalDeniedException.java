package com.moara.moa.access;

/** 승인 권한이 없는 사용자가 승인/반려/회수를 시도할 때. */
public class AccessApprovalDeniedException extends RuntimeException {
  public AccessApprovalDeniedException() {
    super("이 요청을 승인할 권한이 없습니다.");
  }
}
