package com.moara.moa.mail;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Properties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/**
 * 스코프 SMTP 설정으로 메일을 발송한다. 설정마다 즉석에서 발송기를 구성하며(전역 JavaMailSender 없음),
 * 본문은 순수 텍스트(SimpleMailMessage)로만 보낸다(HTML 미허용 → 주입 위험 제거).
 */
@Service
public class MailService {
  private final MailSettingService settingService;

  public MailService(MailSettingService settingService) {
    this.settingService = settingService;
  }

  /** 설정 자체 점검용 테스트 메일 1통. 성공/실패 여부만 반환(예외는 상위에서 메시지로 표기). */
  public void sendTest(MailSetting setting, String to) {
    JavaMailSenderImpl sender = build(setting);
    SimpleMailMessage message = baseMessage(setting);
    message.setTo(to.trim());
    message.setSubject("[MOA] 메일 설정 테스트");
    message.setText("MOA 메일(SMTP) 설정이 정상 동작합니다.\n이 메일을 받으셨다면 설정이 올바릅니다.");
    sender.send(message);
  }

  /**
   * 여러 수신자에게 동일 본문을 개별 발송한다. 실패한 수신자는 건너뛰고 집계만 반환한다
   * (한 명 실패가 전체를 막지 않도록). 수신자별 개별 메시지로 보내 상호 주소 노출을 막는다.
   */
  public MailSendResult sendBulk(MailSetting setting, List<String> recipients, String subject, String body) {
    JavaMailSenderImpl sender = build(setting);
    int sent = 0;
    int failed = 0;
    for (String recipient : recipients) {
      if (recipient == null || recipient.isBlank()) {
        continue;
      }
      try {
        SimpleMailMessage message = baseMessage(setting);
        message.setTo(recipient.trim());
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
        sent++;
      } catch (RuntimeException exception) {
        failed++;
      }
    }
    return new MailSendResult(sent, failed);
  }

  private SimpleMailMessage baseMessage(MailSetting setting) {
    SimpleMailMessage message = new SimpleMailMessage();
    String from = setting.getFromName() != null && !setting.getFromName().isBlank()
        ? setting.getFromName() + " <" + setting.getFromAddress() + ">"
        : setting.getFromAddress();
    message.setFrom(from);
    return message;
  }

  private JavaMailSenderImpl build(MailSetting setting) {
    verifyHostAllowed(setting.getHost());
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(setting.getHost());
    sender.setPort(setting.getPort());
    sender.setDefaultEncoding("UTF-8");
    String password = settingService.decryptPassword(setting);
    if (setting.getUsername() != null && !setting.getUsername().isBlank()) {
      sender.setUsername(setting.getUsername());
      sender.setPassword(password == null ? "" : password);
    }
    Properties props = sender.getJavaMailProperties();
    props.put("mail.transport.protocol", "smtp");
    props.put("mail.smtp.auth", String.valueOf(setting.getUsername() != null && !setting.getUsername().isBlank()));
    props.put("mail.smtp.starttls.enable", String.valueOf(setting.isStarttls()));
    props.put("mail.smtp.connectiontimeout", "10000");
    props.put("mail.smtp.timeout", "10000");
    props.put("mail.smtp.writetimeout", "10000");
    return sender;
  }

  /**
   * SMTP 호스트가 내부/사설 대역을 가리키지 않는지 확인한다(SSRF 방지). 루프백·링크로컬(메타데이터
   * 169.254.169.254 포함)·사설망(RFC1918)·와일드카드·멀티캐스트로 해석되면 발송을 거부한다.
   * 해석된 모든 주소를 검사하며, 해석 불가 시에도 거부한다.
   */
  private void verifyHostAllowed(String host) {
    if (host == null || host.isBlank()) {
      throw new MailNotAllowedException("SMTP 호스트가 지정되지 않았습니다.");
    }
    InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(host.trim());
    } catch (UnknownHostException exception) {
      throw new MailNotAllowedException("SMTP 호스트를 확인할 수 없습니다.");
    }
    for (InetAddress address : addresses) {
      if (address.isLoopbackAddress() || address.isLinkLocalAddress()
          || address.isSiteLocalAddress() || address.isAnyLocalAddress()
          || address.isMulticastAddress()) {
        throw new MailNotAllowedException("내부/사설 대역의 SMTP 호스트는 허용되지 않습니다.");
      }
    }
  }
}
