package com.moara.moa.permission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 묶음 권한 생성/수정 폼. */
public record PermissionForm(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 255) String description,
    @NotNull PermissionStatus status) {}
