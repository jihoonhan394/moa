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
    String secret) {

  /**
   * record 기본 toString은 모든 필드를 그대로 찍으므로 secret이 로그·예외 메시지에 평문으로
   * 남는다(CWE-532). 다른 비밀번호 폼과 동일하게 마스킹한다.
   */
  @Override
  public String toString() {
    return "CredentialForm[name=" + name + ", type=" + type + ", username=" + username
        + ", secret=" + (secret == null || secret.isBlank() ? "<none>" : "***") + "]";
  }
}
