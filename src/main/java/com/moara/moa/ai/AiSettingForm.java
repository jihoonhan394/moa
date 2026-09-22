package com.moara.moa.ai;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** AI 설정 폼. apiKey는 선택(빈 값이면 기존 키 유지). toString은 키를 마스킹(로그 유출 방지). */
public record AiSettingForm(
    @NotNull AiProvider provider,
    @Size(max = 100) String model,
    @Size(max = 255) String baseUrl,
    @Size(max = 255) String apiKey,
    boolean enabled) {

  public static AiSettingForm from(AiSetting setting) {
    if (setting == null) {
      return new AiSettingForm(AiProvider.DEEPSEEK, null, null, "", false);
    }
    return new AiSettingForm(
        setting.getProvider(), setting.getModel(), setting.getBaseUrl(), "", setting.isEnabled());
  }

  @Override
  public String toString() {
    return "AiSettingForm{provider=" + provider + ", model=" + model + ", baseUrl=" + baseUrl
        + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "<none>" : "***") + ", enabled=" + enabled + "}";
  }
}
