package com.moara.moa.wiki;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 위키 서식 생성·수정 폼. */
public record WikiTemplateForm(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 50000) String content) {}
