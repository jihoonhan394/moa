package com.moara.moa.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moara.moa.mail.MailSendResult;
import com.moara.moa.mail.MailService;
import com.moara.moa.mail.MailSetting;
import com.moara.moa.mail.MailSettingService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 알림 메일 발송기. 검증의 초점은 "언제 보내느냐"가 아니라 <b>언제 보내지 않고도 조용히
 * 넘어가느냐</b> 다 — 이 층에서 예외가 올라가면 배치가 통째로 멈추고, 아직 알림을 받지 못한
 * 뒤쪽 기관이 피해를 본다.
 */
class NotificationMailerTest {
  private static final UUID TENANT = UUID.randomUUID();

  private final MailSettingService settingService = mock(MailSettingService.class);
  private final MailService mailService = mock(MailService.class);

  private NotificationMailer mailer(String baseUrl) {
    return new NotificationMailer(settingService, mailService, baseUrl);
  }

  private void smtpConfigured(int sent, int failed) {
    when(settingService.findForTenant(TENANT)).thenReturn(Optional.of(mock(MailSetting.class)));
    when(mailService.sendBulk(any(), any(), anyString(), anyString()))
        .thenReturn(new MailSendResult(sent, failed));
  }

  /** 기관이 SMTP를 안 걸었으면 실패가 아니라 건너뛰기다. 인앱 알림은 이미 기록돼 있다. */
  @Test
  void 기관_SMTP가_없으면_조용히_건너뛴다() {
    when(settingService.findForTenant(TENANT)).thenReturn(Optional.empty());

    int sent = mailer("").send(TENANT, List.of("a@example.com"), "제목", "본문", "/expirations");

    assertThat(sent).isZero();
    verify(mailService, never()).sendBulk(any(), any(), anyString(), anyString());
  }

  /** SMTP 예외가 배치로 전파되면 안 된다 — 메일은 보조 경로다. */
  @Test
  void 발송_실패는_예외로_올라가지_않는다() {
    when(settingService.findForTenant(TENANT)).thenReturn(Optional.of(mock(MailSetting.class)));
    when(mailService.sendBulk(any(), any(), anyString(), anyString()))
        .thenThrow(new IllegalStateException("SMTP 연결 실패(테스트)"));

    int sent = mailer("").send(TENANT, List.of("a@example.com"), "제목", "본문", "/expirations");

    assertThat(sent).isZero();
  }

  @Test
  void 수신자가_없으면_설정을_조회하지도_않는다() {
    int sent = mailer("").send(TENANT, List.of(), "제목", "본문", "/expirations");

    assertThat(sent).isZero();
    verify(settingService, never()).findForTenant(any());
  }

  @Test
  void 발송_성공하면_보낸_통수를_돌려준다() {
    smtpConfigured(3, 0);

    int sent = mailer("").send(
        TENANT, List.of("a@example.com", "b@example.com", "c@example.com"), "제목", "본문", null);

    assertThat(sent).isEqualTo(3);
  }

  /** 일부 수신자 실패는 발송 자체의 실패가 아니다(MailService가 이미 건너뛴다). */
  @Test
  void 일부_수신자_실패해도_성공분을_돌려준다() {
    smtpConfigured(2, 1);

    assertThat(mailer("").send(TENANT, List.of("a@x.com", "b@x.com", "c@x.com"), "제", "본", null))
        .isEqualTo(2);
  }

  /** base-url이 설정돼 있으면 절대 링크를 붙인다. 배치엔 요청이 없어 이 설정이 유일한 근거다. */
  @Test
  void base_url이_있으면_절대_링크를_덧붙인다() {
    smtpConfigured(1, 0);

    mailer("https://moa.example.com")
        .send(TENANT, List.of("a@example.com"), "제목", "본문", "/expirations");

    assertThat(capturedBody()).isEqualTo("본문\n\n자세히 보기: https://moa.example.com/expirations");
  }

  @Test
  void base_url_끝의_슬래시가_중복되지_않는다() {
    smtpConfigured(1, 0);

    mailer("https://moa.example.com/")
        .send(TENANT, List.of("a@example.com"), "제목", "본문", "/expirations");

    assertThat(capturedBody()).endsWith("https://moa.example.com/expirations");
  }

  /** 틀린 링크를 넣느니 빼는 편이 낫다 — 호스트를 유추할 근거가 없기 때문이다. */
  @Test
  void base_url이_없으면_링크_줄을_생략한다() {
    smtpConfigured(1, 0);

    mailer("").send(TENANT, List.of("a@example.com"), "제목", "본문", "/expirations");

    assertThat(capturedBody()).isEqualTo("본문");
  }

  private String capturedBody() {
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(mailService).sendBulk(any(), any(), eq("제목"), body.capture());
    return body.getValue();
  }
}
