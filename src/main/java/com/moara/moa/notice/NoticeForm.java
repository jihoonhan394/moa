package com.moara.moa.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공지 작성/수정 폼. emailToUsers=true면 저장 후 기관 사용자에게 이메일로도 발송한다. */
public record NoticeForm(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 4000) String body,
    boolean pinned,
    boolean emailToUsers) {}
