package com.moara.moa.mail;

/** 대량 발송 집계. sent=성공 통수, failed=실패 통수. */
public record MailSendResult(int sent, int failed) {
  public int total() {
    return sent + failed;
  }
}
