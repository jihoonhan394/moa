package com.moara.moa.credential;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 자격증명 생성/수정 폼. {@code secret}은 입력 전용(평문)이며 저장 시 즉시 암호화된다.
 * 수정 시 secret이 비면 기존 비밀을 유지한다(쓰기전용: 평문은 다시 보여주지 않음).
 */
public record CredentialForm(
    @NotBlank @Size(max = 100) String name,
    @NotNull CredentialType type,
    @NotBlank @Size(max = 255) String username,
    String secret) {}
