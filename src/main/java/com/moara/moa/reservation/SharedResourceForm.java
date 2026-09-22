package com.moara.moa.reservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공유자산 등록·수정 폼. type(고정 enum) → category(관리형 카테고리 String)로 대체. */
public record SharedResourceForm(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 100) String category,
    @Size(max = 200) String location,
    @Min(0) Integer capacity,
    SharedResourceStatus status,
    @Size(max = 500) String description) {}
