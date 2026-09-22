package com.moara.moa.wiki;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 위키 공간(폴더) 생성·수정 폼. */
public record WikiSpaceForm(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description) {}
