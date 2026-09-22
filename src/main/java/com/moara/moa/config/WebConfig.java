package com.moara.moa.config;

import com.moara.moa.security.TenantFeatureInterceptor;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 기능 엔타이틀먼트 인터셉터 + 정적 리소스 캐시 정책. */
@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final TenantFeatureInterceptor tenantFeatureInterceptor;

  public WebConfig(TenantFeatureInterceptor tenantFeatureInterceptor) {
    this.tenantFeatureInterceptor = tenantFeatureInterceptor;
  }

  /**
   * 웹폰트는 자주 안 바뀌므로 장기 캐시한다(브라우저가 한 번만 받고 이후 이동엔 즉시 사용 →
   * font-display: optional과 함께 페이지 이동 시 폰트 스왑 깜빡임/크기 점프를 없앤다).
   * CSS/JS는 배포 시 갱신되어야 하므로 여기서 장기 캐시하지 않는다(기본 처리 유지).
   */
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/fonts/**")
        .addResourceLocations("classpath:/static/fonts/")
        .setCacheControl(CacheControl.maxAge(Duration.ofDays(180)).cachePublic());
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(tenantFeatureInterceptor)
        .addPathPatterns(
            "/assets", "/assets/**",
            "/servers", "/servers/**",
            "/server-status", "/server-status/**",
            "/portal", "/portal/**",
            "/connections", "/connections/**",
            "/solutions", "/solutions/**",
            "/solution-sequences", "/solution-sequences/**",
            "/credentials", "/credentials/**",
            "/inventory", "/inventory/**",
            "/shared-resources", "/shared-resources/**",
            "/reservations", "/reservations/**",
            "/wiki", "/wiki/**");
  }
}
