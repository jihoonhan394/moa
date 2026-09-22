package com.moara.moa.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 데모 데이터 초기화. {@code moa.sample-data.reset=true}일 때만, 시더(@Order 1,2)보다 먼저(@Order 0) 실행되어
 * {@code tenants}/{@code flyway_schema_history}를 제외한 public 테이블을 모두 비운다(CASCADE).
 * 운영 데이터가 있는 DB에서는 절대 켜지 않는다. 일회성 시드/데모 재구성용.
 */
@Configuration
@ConditionalOnProperty(name = "moa.sample-data.reset", havingValue = "true")
public class SampleDataReset {
  private static final Logger log = LoggerFactory.getLogger(SampleDataReset.class);

  @Bean
  @Order(0)
  CommandLineRunner resetSampleData(JdbcTemplate jdbc) {
    return arguments -> {
      List<String> tables = jdbc.queryForList(
          "SELECT tablename FROM pg_tables WHERE schemaname = 'public' "
              + "AND tablename NOT IN ('flyway_schema_history', 'tenants')",
          String.class);
      if (tables.isEmpty()) {
        return;
      }
      String joined = tables.stream().map(name -> "\"" + name + "\"").reduce((a, b) -> a + ", " + b).orElse("");
      jdbc.execute("TRUNCATE TABLE " + joined + " RESTART IDENTITY CASCADE");
      log.warn("SAMPLE-RESET: truncated {} tables ({})", tables.size(), String.join(",", tables));
    };
  }
}
