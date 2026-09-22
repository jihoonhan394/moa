package com.moara.moa.solution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 기동 순서 생성·수정 폼. */
public record SolutionSequenceForm(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description) {}
