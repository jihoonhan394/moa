package com.moara.moa.notification;

import com.moara.moa.mail.MailSendResult;
import com.moara.moa.mail.MailService;
import com.moara.moa.mail.MailSetting;
import com.moara.moa.mail.MailSettingService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 인앱 알림을 메일로도 내보낸다. 배치(만료 임박 등)는 요청 맥락이 없어 사용자가 앱에 들어오기
 * 전까지 종 아이콘을 보지 못하므로, 메일이 실제 전달 경로다.
 *
 * <h2>실패해도 배치를 죽이지 않는다</h2>
 * 기관이 SMTP를 설정하지 않았으면 <b>실패가 아니라 건너뛰기</b>다. 발송 중 예외도 삼키고 로그만
 * 남긴다. 알림의 본체는 이미 DB에 기록돼 있고, 메일은 그 사본을 밀어 주는 보조 경로다 — 여기서
 * 예외를 올리면 아직 알림을 못 받은 뒤쪽 기관까지 배치가 통째로 멈춘다.
 *
 * <h2>건별이 아니라 묶음으로 보낸다</h2>
 * 만료 임박 항목이 10건이면 메일도 10통이 되어선 안 된다. 호출자가 수신자별로 모아 한 통으로
 * 넘긴다. 중복 방지는 {@link NotificationService#notifyOnce}가 이미 하므로 여기선 하지 않는다.
 */
@Component
public class NotificationMailer {
  private static final Logger log = LoggerFactory.getLogger(NotificationMailer.class);

  private final MailSettingService settingService;
  private final MailService mailService;
  private final String baseUrl;

  public NotificationMailer(
      MailSettingService settingService, MailService mailService,
      @Value("${moa.base-url:}") String baseUrl) {
    this.settingService = settingService;
    this.mailService = mailService;
    this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
  }

  /**
   * 한 기관의 수신자들에게 같은 본문을 보낸다. 보낸 통수를 반환하며, 건너뛰었으면 0이다.
   *
   * @param link 앱 안의 경로(예: {@code /expirations}). {@code moa.base-url}이 설정돼 있으면
   *     절대 URL로 덧붙이고, 없으면 링크 줄을 생략한다 — 배치엔 요청이 없어 호스트를 유추할 수
   *     없고, 틀린 링크를 넣느니 빼는 편이 낫다.
   */
  public int send(UUID tenantId, List<String> recipients, String subject, String body, String link) {
    if (recipients == null || recipients.isEmpty()) {
      return 0;
    }
    Optional<MailSetting> setting = settingService.findForTenant(tenantId);
    if (setting.isEmpty()) {
      log.debug("기관 SMTP 미설정 — 알림 메일을 건너뜁니다. tenantId={}", tenantId);
      return 0;
    }
    try {
      MailSendResult result =
          mailService.sendBulk(setting.get(), recipients, subject, withLink(body, link));
      if (result.failed() > 0) {
        log.warn("알림 메일 일부 실패. tenantId={} 성공={} 실패={}",
            tenantId, result.sent(), result.failed());
      }
      return result.sent();
    } catch (RuntimeException failure) {
      log.warn("알림 메일 발송 실패 — 인앱 알림은 이미 기록됐습니다. tenantId={}", tenantId, failure);
      return 0;
    }
  }

  private String withLink(String body, String link) {
    if (baseUrl.isEmpty() || link == null || link.isBlank()) {
      return body;
    }
    String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    return body + "\n\n자세히 보기: " + base + link;
  }
}
