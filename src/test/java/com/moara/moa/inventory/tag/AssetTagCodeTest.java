package com.moara.moa.inventory.tag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssetTagCodeTest {

  @Test
  @DisplayName("만들어진 코드는 MOA-XXXX-XXXX 형태이고 스스로 검증을 통과한다")
  void 생성한_코드는_검증을_통과한다() {
    for (int attempt = 0; attempt < 500; attempt++) {
      String code = AssetTagCode.generate();
      assertThat(code).matches("MOA-[0-9A-Z]{4}-[0-9A-Z]{4}");
      assertThat(AssetTagCode.isValid(code)).isTrue();
    }
  }

  @Test
  @DisplayName("혼동하기 쉬운 글자는 알파벳에 아예 없다")
  void 혼동문자가_없다() {
    Set<Character> used = new HashSet<>();
    for (int attempt = 0; attempt < 2000; attempt++) {
      for (char character : AssetTagCode.generate().replace("MOA-", "").replace("-", "").toCharArray()) {
        used.add(character);
      }
    }
    // I·L·O·U 를 쓰지 않는다. 사람이 받아 적을 때 1·0·V 와 헷갈리기 때문이다.
    assertThat(used).doesNotContain('I', 'L', 'O', 'U');
  }

  @Test
  @DisplayName("사람이 입력한 소문자·공백·붙임표를 받아 준다")
  void 입력을_너그럽게_받는다() {
    String code = AssetTagCode.generate(fixed(1, 2, 3, 4, 5, 6, 7));
    String body = code.replace("MOA-", "").replace("-", "");

    assertThat(AssetTagCode.normalize(code)).isEqualTo(code);
    assertThat(AssetTagCode.normalize(code.toLowerCase())).isEqualTo(code);
    assertThat(AssetTagCode.normalize(" " + code + " ")).isEqualTo(code);
    assertThat(AssetTagCode.normalize(body)).isEqualTo(code);
    assertThat(AssetTagCode.normalize("moa " + body)).isEqualTo(code);
  }

  @Test
  @DisplayName("I·L 은 1 로, O 는 0 으로 고쳐 읽는다")
  void 혼동문자를_고쳐_읽는다() {
    // 1 로 시작하는 코드를 만들어 I/L 로 바꿔 입력해도 같은 코드로 읽히는지 본다.
    String code = AssetTagCode.generate(fixed(1, 1, 1, 1, 1, 1, 1));
    String body = code.replace("MOA-", "").replace("-", "");
    assertThat(body).startsWith("1111111");

    assertThat(AssetTagCode.normalize(body.replace('1', 'I'))).isEqualTo(code);
    assertThat(AssetTagCode.normalize(body.replace('1', 'l'))).isEqualTo(code);
  }

  @Test
  @DisplayName("⚠️ 한 글자를 잘못 읽으면 통과하지 않는다")
  void 한글자_오류를_잡는다() {
    String code = AssetTagCode.generate(fixed(5, 9, 2, 14, 7, 3, 21));
    String body = code.replace("MOA-", "").replace("-", "");

    int broken = 0;
    for (int index = 0; index < body.length(); index++) {
      for (char replacement : "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray()) {
        if (replacement == body.charAt(index)) {
          continue;
        }
        String mutated = body.substring(0, index) + replacement + body.substring(index + 1);
        assertThat(AssetTagCode.normalize(mutated))
            .as("한 글자만 바꾼 %s 는 거부되어야 한다", mutated)
            .isNull();
        broken++;
      }
    }
    assertThat(broken).isEqualTo(8 * 31);
  }

  @Test
  @DisplayName("이웃한 두 글자가 뒤바뀌면 거의 다 잡는다 — 가중치를 자리마다 다르게 둔 이유다")
  void 전치_오류를_거의_다_잡는다() {
    int swapped = 0;
    int caught = 0;
    for (int attempt = 0; attempt < 400; attempt++) {
      String body = AssetTagCode.generate().replace("MOA-", "").replace("-", "");
      for (int index = 0; index + 1 < body.length(); index++) {
        char left = body.charAt(index);
        char right = body.charAt(index + 1);
        if (left == right) {
          continue;
        }
        String mutated = body.substring(0, index) + right + left + body.substring(index + 2);
        swapped++;
        if (AssetTagCode.normalize(mutated) == null) {
          caught++;
        }
      }
    }
    // 홀수 가중치끼리의 차는 짝수라, 값 차이가 정확히 16 인 전치만 빠져나간다(약 3%).
    // 그것까지 잡으려면 32진수를 버려야 하고, QR 자체에 오류 정정이 있으므로 거기까지 가지 않는다.
    assertThat(swapped).isGreaterThan(1000);
    assertThat((double) caught / swapped).isGreaterThan(0.95);
  }

  @Test
  @DisplayName("코드가 아닌 값은 예외가 아니라 null 이다")
  void 코드가_아니면_null() {
    assertThat(AssetTagCode.normalize(null)).isNull();
    assertThat(AssetTagCode.normalize("")).isNull();
    assertThat(AssetTagCode.normalize("   ")).isNull();
    assertThat(AssetTagCode.normalize("MOA-7K3F")).isNull();
    assertThat(AssetTagCode.normalize("MOA-7K3F-2M9B-EXTRA")).isNull();
    assertThat(AssetTagCode.normalize("한글입니다")).isNull();
    assertThat(AssetTagCode.normalize("https://evil.example/MOA-7K3F-2M9B")).isNull();
    // 검사문자가 맞지 않는 값. (MOA-0000-0000 은 0 들의 가중합이 0 이라 실제로 유효한 코드다.)
    assertThat(AssetTagCode.isValid("MOA-0000-0001")).isFalse();
  }

  @Test
  @DisplayName("코드가 겹치지 않는다")
  void 코드가_겹치지_않는다() {
    Set<String> codes = new HashSet<>();
    for (int attempt = 0; attempt < 20_000; attempt++) {
      codes.add(AssetTagCode.generate());
    }
    assertThat(codes).hasSize(20_000);
  }

  /** 난수원을 고정해 코드를 재현 가능하게 만든다. */
  private static RandomGenerator fixed(int... values) {
    return new RandomGenerator() {
      private int cursor = 0;

      @Override
      public int nextInt(int bound) {
        return values[cursor++ % values.length] % bound;
      }

      @Override
      public long nextLong() {
        return nextInt(Integer.MAX_VALUE);
      }
    };
  }
}
