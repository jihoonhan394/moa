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
 * {@code managed_users.username} 에 남아 있는 유일 제약을 <b>이름과 무관하게</b> 없앤다.
 *
 * <p>왜 Java 마이그레이션인가 — 제약의 이름이 DB 엔진마다 다르기 때문이다.
 *
 * <pre>
 *   V2   username VARCHAR(50) NOT NULL UNIQUE        ← 이름 없는 컬럼 제약
 *   V16  DROP CONSTRAINT IF EXISTS managed_users_username_key
 *        PostgreSQL 이 붙이는 이름이다. H2 는 CONSTRAINT_F8 처럼 붙이므로 **아무 일도 안 한다**
 *   V55  DROP CONSTRAINT IF EXISTS ux_managed_users_tenant_username
 * </pre>
 *
 * <p>그래서 운영(PostgreSQL)에는 제약이 없고 로컬·테스트(H2)에는 남아 있었다. V16 주석은 그
 * 분기를 알고 있었고 "테스트는 기관별 중복 아이디를 넣지 않아 영향이 없다"고 적어 두었는데,
 * <b>초대 수락이 표시 이름에서 username 을 만들면서 그 전제가 깨졌다</b> — 동명이인을 초대하면
 * 로컬에서만 가입이 실패한다.
 *
 * <p>로그인 키는 이메일이고 username 은 표시 이름(핸들)이므로(V53) 동명이인은 정상이다.
 * 여기서는 {@code information_schema} 로 실제 제약을 찾아 지운다 — 이름을 추측하지 않는다.
 * 이메일의 유일 제약({@code ux_managed_users_tenant_email})은 건드리지 않는다.
 */
public class V75__Drop_legacy_username_unique extends BaseJavaMigration {

  private static final Logger log =
      LoggerFactory.getLogger(V75__Drop_legacy_username_unique.class);

  /** username 한 컬럼만으로 이루어진 UNIQUE 제약을 찾는다. 복합(tenant_id, email)은 걸리지 않는다. */
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
         and min(lower(kcu.column_name)) = 'username'
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
      log.info("managed_users.username 에 남은 유일 제약이 없다 — 지울 것이 없다.");
      return;
    }

    for (String name : names) {
      // 제약 이름은 information_schema 에서 읽은 값이라 사용자 입력이 아니다.
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "ALTER TABLE managed_users DROP CONSTRAINT \"" + name.replace("\"", "\"\"") + "\"");
        log.info("managed_users.username 의 유일 제약 '{}' 를 제거했다(동명이인 허용).", name);
      }
    }
  }
}
