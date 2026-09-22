package com.moara.moa.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserForm(
    // 표시 이름(핸들). 로그인 키는 이메일이며 username은 이메일이 아니다 → @ 불가, 한글 허용.
    @NotBlank @Pattern(regexp = "[\\p{L}\\p{N} ._-]+",
        message = "이름에는 @를 쓸 수 없습니다(이메일은 아래 이메일 칸에).")
    @Size(max = 100) String username,
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = 30) @Pattern(regexp = "[0-9+\\-() ]{7,30}",
        message = "전화번호는 숫자와 -, +, (), 공백만 사용할 수 있습니다.") String phone,
    @Size(max = 100) String password,
    @NotNull UserStatus status) {

  /** 전화번호 도입 이전 호출부(프로그램/테스트) 호환용. phone은 미지정(null)으로 둔다. */
  public UserForm(String username, String name, String email, String password, UserStatus status) {
    this(username, name, email, null, password, status);
  }

  /** 비밀번호가 로그/예외에 노출되지 않도록 마스킹한다(record 기본 toString 유출 방지, CWE-532). */
  @Override
  public String toString() {
    return "UserForm[username=" + username + ", name=" + name + ", email=" + email
        + ", phone=" + phone
        + ", password=" + (password == null || password.isBlank() ? "<none>" : "***")
        + ", status=" + status + "]";
  }
}
