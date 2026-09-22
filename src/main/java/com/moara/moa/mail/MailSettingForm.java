package com.moara.moa.mail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * SMTP 설정 입력 폼. password는 선택값 — 비우면 기존 저장된 비밀번호를 유지한다(교체할 때만 입력).
 */
public record MailSettingForm(
    @NotBlank @Size(max = 255) String host,
    @Min(1) @Max(65535) int port,
    @Size(max = 255) String username,
    @Size(max = 255) String password,
    @NotBlank @Email @Size(max = 255) String fromAddress,
    @Size(max = 100) String fromName,
    boolean starttls,
    boolean enabled) {

  public static MailSettingForm from(MailSetting setting) {
    if (setting == null) {
      return new MailSettingForm("", 587, "", "", "", "", true, false);
    }
    return new MailSettingForm(
        setting.getHost() == null ? "" : setting.getHost(),
        setting.getPort() == 0 ? 587 : setting.getPort(),
        setting.getUsername() == null ? "" : setting.getUsername(),
        "",
        setting.getFromAddress() == null ? "" : setting.getFromAddress(),
        setting.getFromName() == null ? "" : setting.getFromName(),
        setting.isStarttls(),
        setting.isEnabled());
  }

  /** SMTP 비밀번호가 로그/예외에 노출되지 않도록 마스킹한다(record 기본 toString 유출 방지, CWE-532). */
  @Override
  public String toString() {
    return "MailSettingForm[host=" + host + ", port=" + port + ", username=" + username
        + ", password=" + (password == null || password.isBlank() ? "<none>" : "***")
        + ", fromAddress=" + fromAddress + ", fromName=" + fromName
        + ", starttls=" + starttls + ", enabled=" + enabled + "]";
  }
}
