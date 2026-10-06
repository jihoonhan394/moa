package com.moara.moa.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.TenantService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 임포트 화면에 적힌 <b>예시 CSV 를 그 화면에서 떼어다</b> 실제로 돌려 본다.
 *
 * <p>왜 이런 테스트가 필요한가: 분류 검사를 넣었을 때 화면의 예시가 전부 거부되는 상태였다
 * (`노트북`·`라이선스`·`가전` — 기본 분류 트리에 없는 이름들). 안내가 거짓이면 사용자는
 * 자기가 뭘 잘못했는지 알아낼 방법이 없다. 예시를 문자열로 베껴 두면 화면만 고치고 이 테스트는
 * 통과하는 일이 생기므로, <b>템플릿에서 읽는다</b>.
 */
@SpringBootTest
@ActiveProfiles("test")
class ImportHelpExampleTest {

  private static final Path TEMPLATE =
      Path.of("src/main/resources/templates/inventory/import.html");

  @Autowired private InventoryImportService importService;
  @Autowired private TenantService tenantService;

  @Test
  @DisplayName("⚠️ 안내 화면의 예시 CSV 는 그대로 붙여넣어도 전부 등록돼야 한다")
  void 화면의_예시는_실제로_통과한다() throws Exception {
    String example = exampleCsvFromTemplate();
    UUID tenant = tenantService.createTenant(
        new CreateTenantCommand("예시사", "EX" + System.nanoTime())).getId();

    InventoryImportResult result = importService.importCsv(tenant, example);

    assertThat(result.errors())
        .as("화면 예시가 거부되면 안내가 거짓이다. 예시:%n%s", example)
        .isEmpty();
    assertThat(result.created()).isEqualTo(3);
  }

  /** import.html 의 &lt;pre&gt; 블록(예시 CSV)을 꺼낸다. */
  private String exampleCsvFromTemplate() throws Exception {
    String html = Files.readString(TEMPLATE);
    Matcher matcher = Pattern.compile("<pre[^>]*>(.*?)</pre>", Pattern.DOTALL).matcher(html);
    assertThat(matcher.find()).as("예시 <pre> 블록을 찾지 못했다").isTrue();
    return matcher.group(1).trim() + "\n";
  }
}
