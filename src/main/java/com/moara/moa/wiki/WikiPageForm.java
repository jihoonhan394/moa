package com.moara.moa.wiki;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 위키 작성·수정 폼. 내용은 평문(마크다운/HTML 렌더링 없음). */
public record WikiPageForm(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 50000) String content) {}
