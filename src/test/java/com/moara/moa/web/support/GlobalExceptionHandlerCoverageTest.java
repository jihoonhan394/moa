package com.moara.moa.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * {@code *NotFoundException} 이 {@link GlobalExceptionHandler} 에 <b>빠짐없이</b> 등록되어
 * 있는지 패키지를 훑어 확인한다.
 *
 * <h2>왜 사람이 기억하지 않게 하나</h2>
 *
 * 등록을 빠뜨리면 그 도메인의 "없는 id" 요청이 <b>whitelabel 500</b> 으로 나간다. 사용자는
 * "잘못된 주소"가 아니라 "시스템이 고장났다"로 읽는다.
 *
 * <p>이 목록은 이미 두 번 어긋났다 — 처음엔 셋만 등록돼 {@code /my/assets/{id}} 가 500 이었고,
 * 전 도메인을 채운 뒤에도 {@code TenantNotFoundException} 이 빠져
 * {@code /admin/tenants/{없는 id}} 가 500 이었다(2026-10-07, 적대적 입력 시험에서 발견).
 * 주석에 "잊지 말 것"이라고 적어 두는 방식은 두 번 실패했으므로 테스트로 바꾼다.
 *
 * <p>새 도메인을 만들면서 등록을 잊으면 <b>이 테스트가 그 클래스 이름을 대고 깨진다.</b>
 */
class GlobalExceptionHandlerCoverageTest {

  private static final String BASE_PACKAGE = "com.moara.moa";

  @Test
  @DisplayName("모든 *NotFoundException 이 404 핸들러에 등록돼 있다")
  void 모든_NotFoundException_이_등록돼_있다() {
    Set<String> declared = handledExceptionNames();
    Set<String> found = notFoundExceptionNames();

    assertThat(found).as("스캔이 아무것도 못 찾았다면 테스트가 무의미하다").isNotEmpty();

    Set<String> missing = new TreeSet<>(found);
    missing.removeAll(declared);
    assertThat(missing)
        .as("""
            404 핸들러에 등록되지 않은 예외가 있다. 이대로면 그 도메인의 '없는 id' 요청이
            whitelabel 500 으로 나간다. GlobalExceptionHandler 의 @ExceptionHandler 목록에
            추가할 것 — %s""", missing)
        .isEmpty();
  }

  @Test
  @DisplayName("핸들러에 적힌 예외는 실재한다 — 지운 도메인의 잔재가 남지 않게")
  void 등록된_예외는_실재한다() {
    Set<String> notFoundNames = notFoundExceptionNames();
    Set<String> stale = handledExceptionNames().stream()
        .filter(name -> name.endsWith("NotFoundException"))
        .filter(name -> !notFoundNames.contains(name))
        .collect(Collectors.toCollection(TreeSet::new));

    assertThat(stale).as("없는 클래스를 가리키면 컴파일이 막지만, 패키지 이동은 안 막는다").isEmpty();
  }

  /** {@code @ExceptionHandler} 들이 선언한 예외 이름 전체. */
  private static Set<String> handledExceptionNames() {
    return Arrays.stream(GlobalExceptionHandler.class.getDeclaredMethods())
        .map(method -> method.getAnnotation(ExceptionHandler.class))
        .filter(annotation -> annotation != null)
        .flatMap(annotation -> Arrays.stream(annotation.value()))
        .map(Class::getSimpleName)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /** 패키지 안의 {@code RuntimeException} 하위 중 이름이 NotFoundException 으로 끝나는 것. */
  private static Set<String> notFoundExceptionNames() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false) {
          @Override
          protected boolean isCandidateComponent(
              org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
            // 기본 구현은 구체 클래스만 통과시키려고 @Component 류를 본다. 예외 클래스는
            // 컴포넌트가 아니므로 그 판단을 건너뛰고 타입 필터 결과만 쓴다.
            return true;
          }
        };
    scanner.addIncludeFilter(new AssignableTypeFilter(RuntimeException.class));
    return scanner.findCandidateComponents(BASE_PACKAGE).stream()
        .map(definition -> definition.getBeanClassName())
        .filter(name -> name != null && name.endsWith("NotFoundException"))
        .map(name -> name.substring(name.lastIndexOf('.') + 1))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /** 스캔이 제대로 도는지 자체 점검 — 알려진 몇 개가 잡혀야 한다. */
  @Test
  @DisplayName("스캐너가 실제로 예외 클래스를 찾는다")
  void 스캐너_자체_점검() {
    Set<String> found = notFoundExceptionNames();
    assertThat(found).contains(
        "AssetNotFoundException", "InventoryItemNotFoundException", "TenantNotFoundException");
    List<String> sample = found.stream().limit(3).toList();
    assertThat(sample).hasSize(3);
  }
}
