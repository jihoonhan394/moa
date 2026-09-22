package com.moara.moa.security;

import com.moara.moa.user.ManagedUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 기동 시 SYSTEM_ADMIN 부트스트랩 계정을 보장한다. 비밀번호는 코드/DB 평문에 남기지 않고
 * {@code MOA_BOOTSTRAP_ADMIN_PASSWORD} env로만 주입한다. env가 비어 있으면 시드를 건너뛴다.
 */
@Configuration
public class AdminAccountBootstrap {
  private static final Logger log = LoggerFactory.getLogger(AdminAccountBootstrap.class);

  @Bean
  CommandLineRunner bootstrapSystemAdmin(
      @Value("${MOA_BOOTSTRAP_ADMIN_USERNAME:admin}") String username,
      @Value("${MOA_BOOTSTRAP_ADMIN_PASSWORD:}") String password,
      ManagedUserService userService) {
    return arguments -> {
      if (password == null || password.isBlank()) {
        // 이미 SYSTEM_ADMIN이 프로비저닝된 운영 환경에선 정상(env 미설정). QA 로그 잡음 방지로 INFO.
        log.info("MOA_BOOTSTRAP_ADMIN_PASSWORD 미설정 — SYSTEM_ADMIN 부트스트랩 시드 생략(이미 프로비저닝됐다면 정상).");
        return;
      }
      userService.ensureSystemAdmin(username, password);
      log.info("SYSTEM_ADMIN 계정 '{}' 보장 완료.", username);
    };
  }
}
