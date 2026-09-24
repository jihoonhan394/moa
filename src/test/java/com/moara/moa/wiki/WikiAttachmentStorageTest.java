package com.moara.moa.wiki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 첨부 <b>저장 방식</b>에 대한 테스트 — 파일이 디스크에 어떻게 쌓이고, 어떤 순서로 지워지고,
 * 무엇이 용량을 막는지. 화면을 거치지 않고 서비스만 직접 만들어 확인한다(검증 대상이 HTTP 흐름이
 * 아니므로). 화면 흐름은 {@code WikiAttachmentFlowTest}가 따로 본다.
 */
class WikiAttachmentStorageTest {

  private static MockMultipartFile file(String name, int bytes) {
    return new MockMultipartFile("file", name, "text/plain", new byte[bytes]);
  }

  /** 메모리에 행을 쌓는 저장소 대역. 이 테스트가 보려는 건 DB가 아니라 파일이다. */
  private static WikiAttachmentRepository fakeRepository(List<WikiAttachment> rows) {
    WikiAttachmentRepository repository = mock(WikiAttachmentRepository.class);
    when(repository.save(any())).thenAnswer(call -> {
      WikiAttachment entity = call.getArgument(0);
      rows.add(entity);
      return entity;
    });
    when(repository.findByTenantIdAndPageIdOrderByCreatedAtDesc(any(), any()))
        .thenAnswer(call -> rows.stream()
            .filter(a -> a.getTenantId().equals(call.getArgument(0))
                && a.getPageId().equals(call.getArgument(1)))
            .toList());
    when(repository.findByIdAndTenantId(any(), any()))
        .thenAnswer(call -> rows.stream()
            .filter(a -> a.getId().equals(call.getArgument(0))
                && a.getTenantId().equals(call.getArgument(1)))
            .findFirst());
    when(repository.sumSizeBytesByTenantId(any()))
        .thenAnswer(call -> rows.stream()
            .filter(a -> a.getTenantId().equals(call.getArgument(0)))
            .mapToLong(WikiAttachment::getSizeBytes)
            .sum());
    doAnswer(call -> {
      WikiAttachment entity = call.getArgument(0);
      rows.removeIf(a -> a.getId().equals(entity.getId()));
      return null;
    }).when(repository).delete(any());
    doAnswer(call -> {
      Iterable<WikiAttachment> entities = call.getArgument(0);
      entities.forEach(e -> rows.removeIf(a -> a.getId().equals(e.getId())));
      return null;
    }).when(repository).deleteAll(any());
    return repository;
  }

  private static long fileCount(Path root) throws Exception {
    try (var walk = Files.walk(root)) {
      return walk.filter(Files::isRegularFile).count();
    }
  }

  /**
   * 저장 경로가 기관/월/샤드로 흩어지는지. 한 디렉터리에 전부 쌓이면 백업·동기화·복구가 파일 수에
   * 비례해 느려지므로, 이 형식 자체가 지켜야 할 약속이다.
   */
  @Test
  void 저장_경로는_기관_월_샤드로_나뉜다(@TempDir Path root) {
    WikiAttachmentService service =
        new WikiAttachmentService(fakeRepository(new ArrayList<>()), root.toString(), 0);
    UUID tenantId = UUID.randomUUID();

    WikiAttachment saved =
        service.store(tenantId, UUID.randomUUID(), UUID.randomUUID(), file("a.txt", 10));

    String[] parts = saved.getStoragePath().split("/");
    assertThat(parts).hasSize(4);
    assertThat(parts[0]).isEqualTo(tenantId.toString());
    assertThat(parts[1]).matches("\\d{4}-\\d{2}");                 // yyyy-MM
    assertThat(parts[2]).isEqualTo(saved.getId().toString().substring(0, 2));
    assertThat(parts[3]).isEqualTo(saved.getId().toString());
    assertThat(root.resolve(saved.getStoragePath())).exists();
  }

  /**
   * 레이아웃을 바꿔도 예전 형식(&lt;기관ID&gt;/&lt;첨부ID&gt;)으로 저장된 파일이 계속 열려야 한다.
   * 경로가 행마다 DB에 있으므로 마이그레이션 없이 공존하는 것이 이 설계의 핵심이다.
   */
  @Test
  void 예전_평면_경로로_저장된_첨부도_계속_열린다(@TempDir Path root) throws Exception {
    WikiAttachmentService service =
        new WikiAttachmentService(fakeRepository(new ArrayList<>()), root.toString(), 0);
    UUID tenantId = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    String legacyPath = tenantId + "/" + id;
    Files.createDirectories(root.resolve(tenantId.toString()));
    Files.writeString(root.resolve(legacyPath), "old");
    WikiAttachment legacy = new WikiAttachment(
        id, tenantId, UUID.randomUUID(), "old.txt", "text/plain", 3, legacyPath,
        UUID.randomUUID(), OffsetDateTime.now());

    assertThat(Files.readString(service.resolve(legacy))).isEqualTo("old");
  }

  /** 기관 용량 상한을 넘기면 저장을 거부하고, 파일도 남기지 않는다. */
  @Test
  void 기관_용량_상한을_넘으면_거부한다(@TempDir Path root) throws Exception {
    WikiAttachmentService service =
        new WikiAttachmentService(fakeRepository(new ArrayList<>()), root.toString(), 1); // 1MB
    UUID tenantId = UUID.randomUUID();
    UUID pageId = UUID.randomUUID();
    service.store(tenantId, pageId, UUID.randomUUID(), file("a.bin", 700 * 1024));

    assertThatThrownBy(
        () -> service.store(tenantId, pageId, UUID.randomUUID(), file("b.bin", 700 * 1024)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("용량 상한");

    assertThat(fileCount(root)).isEqualTo(1);
  }

  /** 상한은 기관별이다 — 다른 기관이 채워 놓았다고 내 업로드가 막히면 안 된다. */
  @Test
  void 용량_상한은_기관마다_따로_센다(@TempDir Path root) {
    WikiAttachmentService service =
        new WikiAttachmentService(fakeRepository(new ArrayList<>()), root.toString(), 1); // 1MB
    service.store(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        file("a.bin", 900 * 1024));

    assertThat(service.store(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        file("b.bin", 900 * 1024))).isNotNull();
  }

  /**
   * 메타 저장이 실패하면 방금 쓴 파일을 되돌린다. 안 되돌리면 아무도 참조하지 않는 파일이
   * 디스크에 영원히 남고, 용량 계산(DB 합계)에도 안 잡혀 추적이 불가능해진다.
   */
  @Test
  void 메타_저장이_실패하면_파일을_되돌린다(@TempDir Path root) throws Exception {
    WikiAttachmentRepository repository = fakeRepository(new ArrayList<>());
    when(repository.save(any())).thenThrow(new IllegalStateException("메타 저장 실패(테스트)"));
    WikiAttachmentService service = new WikiAttachmentService(repository, root.toString(), 0);

    assertThatThrownBy(() -> service.store(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), file("a.txt", 10)))
        .isInstanceOf(IllegalStateException.class);

    assertThat(fileCount(root)).isZero();
  }

  /**
   * 파일이 이미 사라졌어도 메타 삭제는 끝까지 간다. 예전 구현은 파일을 먼저 지우고 실패하면
   * 예외를 던져, 파일 하나 때문에 첨부·문서 삭제 전체가 막힐 수 있었다.
   */
  @Test
  void 파일이_없어도_첨부_삭제는_완료된다(@TempDir Path root) throws Exception {
    List<WikiAttachment> rows = new ArrayList<>();
    WikiAttachmentService service =
        new WikiAttachmentService(fakeRepository(rows), root.toString(), 0);
    UUID tenantId = UUID.randomUUID();
    WikiAttachment saved =
        service.store(tenantId, UUID.randomUUID(), UUID.randomUUID(), file("a.txt", 10));
    Files.delete(root.resolve(saved.getStoragePath())); // 밖에서 사라진 상황

    service.delete(tenantId, saved.getId());

    assertThat(rows).isEmpty();
  }

  /** 문서 삭제 시 그 문서의 첨부 파일 본체까지 사라진다(FK CASCADE는 메타만 지운다). */
  @Test
  void 문서_삭제는_첨부_본체까지_지운다(@TempDir Path root) throws Exception {
    WikiAttachmentService service =
        new WikiAttachmentService(fakeRepository(new ArrayList<>()), root.toString(), 0);
    UUID tenantId = UUID.randomUUID();
    UUID pageId = UUID.randomUUID();
    service.store(tenantId, pageId, UUID.randomUUID(), file("a.txt", 10));
    service.store(tenantId, pageId, UUID.randomUUID(), file("b.txt", 10));

    service.deleteAllForPage(tenantId, pageId);

    assertThat(service.list(tenantId, pageId)).isEmpty();
    assertThat(fileCount(root)).isZero();
  }

  /** 상한 0은 무제한. 용량 확인을 끄고 싶은 기관·개발 환경을 위한 탈출구다. */
  @Test
  void 상한이_0이면_무제한이다(@TempDir Path root) {
    WikiAttachmentService service =
        new WikiAttachmentService(fakeRepository(new ArrayList<>()), root.toString(), 0);
    UUID tenantId = UUID.randomUUID();
    UUID pageId = UUID.randomUUID();
    for (int i = 0; i < 5; i++) {
      service.store(tenantId, pageId, UUID.randomUUID(), file("f" + i, 500 * 1024));
    }
    assertThat(service.list(tenantId, pageId)).hasSize(5);
    assertThat(service.usedBytes(tenantId)).isEqualTo(5L * 500 * 1024);
  }
}
