package com.moara.moa.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 기관(테넌트) 자가 회원가입 신청 폼. 진입 1단계에서 선택된 기관 범위로 접수되며,
 * 생성 시 상태는 PENDING(승인 대기)으로 고정된다. 상태/역할은 신청자가 지정할 수 없다.
 */
public record SignupForm(
    @NotBlank @Pattern(regexp = "[\\p{L}\\p{N} ._-]+",
        message = "이름에는 @를 쓸 수 없습니다.")
    @Size(max = 100) String username,
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = 30) @Pattern(regexp = "[0-9+\\-() ]{7,30}",
        message = "전화번호는 숫자와 -, +, (), 공백만 사용할 수 있습니다.") String phone,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank String passwordConfirm) {

  public boolean passwordsMatch() {
    return password != null && password.equals(passwordConfirm);
  }

  /** 비밀번호가 로그/예외에 노출되지 않도록 마스킹한다(record 기본 toString 유출 방지, CWE-532). */
  @Override
  public String toString() {
    return "SignupForm[username=" + username + ", name=" + name + ", email=" + email
        + ", phone=" + phone + ", password=***, passwordConfirm=***]";
  }
}
