package com.moara.moa.security;

import com.moara.moa.credential.SecretVault;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2단계 인증(TOTP) 등록/검증 오케스트레이션 — <b>토대</b>. 시크릿은 {@link SecretVault} 봉투암호화로만 저장한다.
 * 흐름: 등록 시작(시크릿 생성·인증기 URI 발급) → 코드 확인(활성화) → 이후 verify로 검증.
 * 아직 민감 작업 게이팅에는 연결하지 않는다(후속). 계산은 {@link TotpService}가 담당.
 */
@Service
@Transactional(readOnly = true)
public class TwoFactorService {
  private static final String ISSUER = "MOA";

  private final UserTotpRepository repository;
  private final SecretVault secretVault;
  private final TotpService totpService;

  public TwoFactorService(
      UserTotpRepository repository, SecretVault secretVault, TotpService totpService) {
    this.repository = repository;
    this.secretVault = secretVault;
    this.totpService = totpService;
  }

  /** 등록 시작 결과: 인증기 앱에 넣을 시크릿(수동키)과 otpauth URI(QR용). */
  public record Enrollment(String secret, String provisioningUri) {}

  public boolean isEnabled(UUID userId) {
    return userId != null && repository.findByUserId(userId).map(UserTotp::isEnabled).orElse(false);
  }

  /**
   * 등록 시작: 새 시크릿을 만들어 암호화 저장(미확정)하고, 인증기 앱 등록용 시크릿·URI를 돌려준다.
   * 이미 있으면 시크릿을 새로 교체한다(재등록).
   */
  @Transactional
  public Enrollment startEnrollment(UUID tenantId, UUID userId, String accountLabel) {
    String secret = totpService.generateSecret();
    OffsetDateTime now = OffsetDateTime.now();
    UserTotp entity = repository.findByUserId(userId).orElse(null);
    if (entity == null) {
      entity = new UserTotp(userId, tenantId, secretVault.encrypt(secret, tenantId), now);
    } else {
      entity.setSecret(secretVault.encrypt(secret, tenantId));
      entity.disable(); // 재등록 시 재확인 전까지 비활성
    }
    repository.save(entity);
    return new Enrollment(secret, totpService.provisioningUri(ISSUER, accountLabel, secret));
  }

  /** 코드 확인 → 성공 시 활성화. 등록 시작 전이면 false. */
  @Transactional
  public boolean confirm(UUID userId, String code) {
    UserTotp entity = repository.findByUserId(userId).orElse(null);
    if (entity == null) {
      return false;
    }
    if (!totpService.verify(decrypt(entity), code, System.currentTimeMillis())) {
      return false;
    }
    entity.enable(OffsetDateTime.now());
    return true;
  }

  @Transactional
  public void disable(UUID userId) {
    repository.findByUserId(userId).ifPresent(UserTotp::disable);
  }

  /** 활성화된 사용자의 코드 검증(후속 step-up 게이팅용). 미등록/비활성이면 false. */
  public boolean verify(UUID userId, String code) {
    UserTotp entity = repository.findByUserId(userId).orElse(null);
    if (entity == null || !entity.isEnabled()) {
      return false;
    }
    return totpService.verify(decrypt(entity), code, System.currentTimeMillis());
  }

  private String decrypt(UserTotp entity) {
    return secretVault.decrypt(
        entity.getSecretCiphertext(), entity.getDekWrapped(), entity.getKeyVersion(), entity.getTenantId());
  }
}
