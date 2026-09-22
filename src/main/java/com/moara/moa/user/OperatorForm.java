package com.moara.moa.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 플랫폼 운영자(SYSTEM_ADMIN) 생성 입력. 연락 대응을 위해 이메일·전화번호도 필수. */
public record OperatorForm(
    @NotBlank @Size(max = 50) String username,
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = 30) @Pattern(regexp = "[0-9+\\-() ]{7,30}",
        message = "전화번호는 숫자와 -, +, (), 공백만 사용할 수 있습니다.") String phone,
    @NotBlank @Size(min = 8, max = 100) String password) {

  /** 비밀번호가 로그/예외에 노출되지 않도록 마스킹한다(record 기본 toString 유출 방지, CWE-532). */
  @Override
  public String toString() {
    return "OperatorForm[username=" + username + ", name=" + name + ", email=" + email
        + ", phone=" + phone + ", password=***]";
  }
}
