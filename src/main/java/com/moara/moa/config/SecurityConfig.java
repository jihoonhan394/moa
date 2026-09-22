package com.moara.moa.config;

import com.moara.moa.security.MoaAuthenticationSuccessHandler;
import com.moara.moa.security.PlatformEntryFilter;
import com.moara.moa.security.TenantAuthenticationDetailsSource;
import com.moara.moa.security.TenantAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      TenantAuthenticationProvider tenantAuthenticationProvider,
      TenantAuthenticationDetailsSource tenantAuthenticationDetailsSource,
      PlatformEntryFilter platformEntryFilter,
      MoaAuthenticationSuccessHandler successHandler)
      throws Exception {
    // 인증은 기관(테넌트) 컨텍스트를 반영하는 TenantAuthenticationProvider가 처리한다.
    // (아이디만으로 조회하는 기본 DaoAuthenticationProvider는 이 빈이 있으면 자동구성에서 물러난다.)
    return http
        .authenticationProvider(tenantAuthenticationProvider)
        // 플랫폼(SYSTEM_ADMIN) 은닉 진입 경로 처리. 공개 라우팅/메뉴에 노출되지 않는다.
        .addFilterBefore(platformEntryFilter, UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(authorize -> authorize
            // 진입 1단계(/enter)·2단계(/login)·초대 수락(/invite)과 정적 자원은 공개.
            // 자가 회원가입은 초대 기반으로 대체되어 제거됨.
            .requestMatchers("/enter", "/enter/**", "/login", "/invite/**", "/css/**", "/js/**", "/fonts/**")
            .permitAll()
            // 플랫폼(기관 관리) 콘솔은 SYSTEM_ADMIN 전용. 아래 공통 관리 라우트보다 먼저 매칭돼야 한다.
            .requestMatchers("/admin/**")
            .hasRole("SYSTEM_ADMIN")
            // 공지 작성·수정·삭제는 기관 관리자만(열람 GET은 기관 전원 허용 → 아래 authenticated로 통과).
            .requestMatchers(HttpMethod.POST, "/notices/**")
            .hasRole("TENANT_ADMIN")
            .requestMatchers("/notices/new", "/notices/*/edit")
            .hasRole("TENANT_ADMIN")
            // 기관 메일 콘솔(SMTP 설정·발송)과 접속 이력 조회는 기관 관리자 전용.
            .requestMatchers("/mail/**", "/access-history/**", "/ai-settings/**")
            .hasRole("TENANT_ADMIN")
            // 인프라(서버·솔루션·자격증명)와 접근 권한 부여는 인프라 관리자(INFRA_MANAGER) 전용.
            // (자산 관리자=실물·SW 인벤토리는 별도 도메인 — 인벤토리 모듈 도입 시 자체 라우트를 갖는다.)
            .requestMatchers(
                "/assets/**", "/servers/**", "/server-status/**", "/solutions/**", "/solution-sequences/**",
                "/operations/**", "/credentials/**", "/permissions/**", "/maintenance/**")
            .hasRole("INFRA_MANAGER")
            // 실물·SW 인벤토리와 공유자산(등록)은 자산 관리자(ASSET_MANAGER) 전용.
            // (공유자산 '예약'/reservations는 일반 사용자 소비 화면 → 아래 authenticated + 기능 인터셉터로 게이팅.)
            .requestMatchers("/inventory/**", "/shared-resources/**")
            .hasRole("ASSET_MANAGER")
            // 만료/갱신 통합 대시보드: 관리 권한(기관·인프라·자산 관리자) 누구나 조회.
            .requestMatchers("/expirations", "/expirations/**")
            .hasAnyRole("TENANT_ADMIN", "INFRA_MANAGER", "ASSET_MANAGER")
            // 사람·조직·거버넌스(사용자·그룹·감사)는 기관 관리자(TENANT_ADMIN) 전용.
            // 플랫폼 운영자(SYSTEM_ADMIN)는 소속 기관이 없어 두 부류 모두 접근 불가 — 플랫폼은 /admin/** 만 사용.
            // 메뉴 숨김(UX)만으론 직접 URL을 못 막으므로 여기가 실질 방어선이다.
            .requestMatchers("/users/**", "/groups/**", "/audit/**", "/access-review/**", "/invitations/**")
            .hasRole("TENANT_ADMIN")
            // 온보딩 '템플릿' 구성은 자원을 아는 인프라/자산 관리자. '적용'은 부서장이 팀 관리(/team)에서
            // 하며, /team은 인증 사용자 라우트로 열되 컨트롤러에서 위임 범위(본인 팀)를 강제한다.
            .requestMatchers("/onboarding/**")
            .hasAnyRole("INFRA_MANAGER", "ASSET_MANAGER")
            // 공지 열람(GET /notices, /notices/{id})과 그 외 인증 필요.
            .anyRequest()
            .authenticated())
        .formLogin(form -> form
            .loginPage("/enter")
            .loginProcessingUrl("/login")
            .authenticationDetailsSource(tenantAuthenticationDetailsSource)
            .successHandler(successHandler)
            .failureUrl("/login?error")
            .permitAll())
        .logout(logout -> logout.logoutSuccessUrl("/enter?logout").invalidateHttpSession(true))
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
