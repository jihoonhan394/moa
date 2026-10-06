package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code managed_users.email} 에 남아 있는 <b>전역</b> 유일 제약을 이름과 무관하게 없앤다.
 *
 * <p>V75 와 같은 함정이고, <b>같은 마이그레이션의 바로 옆 줄이다</b> — V75 에서 username 을
 * 고치면서 email 을 놓쳤다.
 *
 * <pre>
 *   V2   email VARCHAR(255) NOT NULL UNIQUE          ← 이름 없는 컬럼 제약
 *   V16  DROP CONSTRAINT IF EXISTS managed_users_username_key
 *        DROP CONSTRAINT IF EXISTS managed_users_email_key   ← PostgreSQL 이 붙이는 이름
 *        H2 는 CONSTRAINT_F8 처럼 붙이므로 **아무 일도 안 한다**
 *   V16  ADD CONSTRAINT ux_managed_users_tenant_email UNIQUE (tenant_id, email)
 * </pre>
 *
 * <p>그래서 운영(PostgreSQL)에는 전역 제약이 없고 로컬·테스트(H2)에만 남아 있었다.
 * 증상은 <b>같은 사람을 두 기관에 둘 수 없다</b>는 것이다 — 서비스는 기관별로만 검사하므로
 * (V16 이 정한 "두레이식" 기관별 신원) 통과하고, 그다음 DB 제약이 터져 <b>500</b> 이 나간다.
 * 외주 담당자·업체 엔지니어처럼 두 기관에 동시에 속하는 사람이 현실적으로 있다.
 *
 * <p>실제로 테스트 데이터 시딩을 두 번 돌렸을 때 기관 관리자 추가가 500 으로 떨어졌다.
 *
 * <p>{@code information_schema} 로 실제 제약을 찾아 지운다 — 이름을 추측하지 않는다.
 * <b>기관별 복합 제약({@code ux_managed_users_tenant_email})은 건드리지 않는다</b> —
 * 한 기관 안에서 이메일은 로그인 키이므로 유일해야 한다({@code having count(*) = 1} 가 가른다).
 */
public class V77__Drop_legacy_email_unique extends BaseJavaMigration {

  private static final Logger log = LoggerFactory.getLogger(V77__Drop_legacy_email_unique.class);

  /** email 한 컬럼만으로 이루어진 UNIQUE 제약. 복합 (tenant_id, email) 은 걸리지 않는다. */
  private static final String FIND_CONSTRAINTS = """
      select tc.constraint_name
        from information_schema.table_constraints tc
        join information_schema.key_column_usage kcu
          on tc.constraint_name = kcu.constraint_name
         and tc.table_name = kcu.table_name
       where tc.constraint_type = 'UNIQUE'
         and lower(tc.table_name) = 'managed_users'
       group by tc.constraint_name
      having count(*) = 1
         and min(lower(kcu.column_name)) = 'email'
      """;

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    List<String> names = new ArrayList<>();
    try (PreparedStatement query = connection.prepareStatement(FIND_CONSTRAINTS);
        ResultSet rows = query.executeQuery()) {
      while (rows.next()) {
        names.add(rows.getString(1));
      }
    }

    if (names.isEmpty()) {
      log.info("managed_users.email 에 남은 전역 유일 제약이 없다 — 지울 것이 없다.");
      return;
    }

    for (String name : names) {
      // 제약 이름은 information_schema 에서 읽은 값이라 사용자 입력이 아니다.
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "ALTER TABLE managed_users DROP CONSTRAINT \"" + name.replace("\"", "\"\"") + "\"");
        log.info("managed_users.email 의 전역 유일 제약 '{}' 를 제거했다(기관별 신원 허용).", name);
      }
    }
  }
}
