package com.moara.moa.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moara.moa.mail.MailSettingForm;
import com.moara.moa.user.OperatorForm;
import com.moara.moa.user.SignupForm;
import com.moara.moa.credential.CredentialForm;
import com.moara.moa.credential.CredentialType;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import org.junit.jupiter.api.Test;

/**
 * 비밀번호를 담는 폼 record의 toString이 평문 비밀번호를 노출하지 않는지 검증한다(CWE-532 로그 유출 방지).
 * record 기본 toString은 모든 필드를 그대로 찍으므로, 각 폼이 마스킹 override를 유지해야 한다.
 */
class CredentialMaskingTest {
  private static final String SECRET = "SUP3R-SECRET-smtp-pw!";

  @Test
  void mailSettingFormMasksPassword() {
    String s = new MailSettingForm(
        "smtp.example.com", 587, "user", SECRET, "no-reply@example.com", "MOA", true, true).toString();
    assertFalse(s.contains(SECRET), "SMTP 비밀번호가 toString에 노출됨");
    assertTrue(s.contains("***"));
  }

  @Test
  void signupFormMasksPassword() {
    String s = new SignupForm("user", "이름", "u@example.com", "010-1234-5678", SECRET, SECRET).toString();
    assertFalse(s.contains(SECRET), "회원가입 비밀번호가 toString에 노출됨");
  }

  @Test
  void userFormMasksPassword() {
    String s = new UserForm("user", "이름", "u@example.com", SECRET, UserStatus.ACTIVE).toString();
    assertFalse(s.contains(SECRET), "사용자 비밀번호가 toString에 노출됨");
  }

  @Test
  void operatorFormMasksPassword() {
    String s = new OperatorForm("op", "운영자", "op@example.com", "010-1234-5678", SECRET).toString();
    assertFalse(s.contains(SECRET), "운영자 비밀번호가 toString에 노출됨");
  }

  @Test
  void credentialFormDoesNotLeakSecret() {
    String s = new CredentialForm("srv-01", CredentialType.PASSWORD, "root", SECRET).toString();
    assertFalse(s.contains(SECRET), "자격증명 secret이 toString에 노출됨");
  }
}
