package com.moara.moa.inventory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 인벤토리 CSV 대량 등록. 열 순서: 이름,유형,카테고리,시리얼,만료일,구매일,보증만료,리스만료,비고.
 * 유형은 PHYSICAL/SOFTWARE 또는 실물/SW. 날짜는 yyyy-MM-dd(빈칸 허용). 헤더 행은 자동 감지해 건너뛴다.
 * 행 단위로 독립 등록하며(부분 성공 허용), 각 등록은 서비스의 단일 트랜잭션·중복검사를 그대로 탄다.
 *
 * <h2>분류 검사 — 여기가 가장 느슨했다</h2>
 *
 * 전에는 분류를 <b>검사 없이 그대로</b> 받았다. 없는 경로가 들어가면 그 자산은 어느 분류
 * 필터에도 안 걸리는데, 임포트는 한 번에 수백 건이라 화면 등록보다 피해가 크다. 그리고
 * 유형을 분류와 <b>따로</b> 받아서 {@code 유형=SOFTWARE, 분류=실물 / 노트북} 같은 모순된
 * 행이 들어갈 수 있었다 — 그러면 유형별 집계와 분류별 집계가 영원히 어긋난다.
 * 규칙은 {@link InventoryCategoryRule} 한 곳에 있다(화면 등록·수정·모바일과 공유).
 */
@Service
public class InventoryImportService {
  private final InventoryItemService inventoryService;
  private final InventoryCategoryRule categoryRule;

  public InventoryImportService(
      InventoryItemService inventoryService, InventoryCategoryRule categoryRule) {
    this.inventoryService = inventoryService;
    this.categoryRule = categoryRule;
  }

  public InventoryImportResult importCsv(UUID tenantId, String csv) {
    int created = 0;
    List<InventoryImportResult.RowError> errors = new ArrayList<>();
    if (csv == null || csv.isBlank()) {
      return new InventoryImportResult(0, errors);
    }
    // BOM 제거(엑셀이 UTF-8로 저장 시 앞에 붙는 경우).
    if (csv.startsWith("﻿")) {
      csv = csv.substring(1);
    }
    String[] lines = csv.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
    // 분류 경로는 행마다 바뀌지 않는다. 행마다 트리를 다시 만들면 1000행이면 1000번이다.
    InventoryCategoryRule.CategoryIndex categories = categoryRule.index(tenantId);
    boolean headerChecked = false;
    for (int i = 0; i < lines.length; i++) {
      String raw = lines[i];
      if (raw.isBlank()) {
        continue;
      }
      List<String> fields = parseCsvLine(raw);
      // 첫 유효 행이 헤더면 건너뛴다.
      if (!headerChecked) {
        headerChecked = true;
        String first = fields.isEmpty() ? "" : fields.get(0).trim().toLowerCase();
        if (first.equals("name") || first.equals("이름")) {
          continue;
        }
      }
      try {
        InventoryItemForm form = toForm(fields, categories);
        inventoryService.create(tenantId, form);
        created++;
      } catch (DuplicateInventoryItemException exception) {
        errors.add(new InventoryImportResult.RowError(i + 1, "이미 존재하는 이름: " + exception.getMessage(), raw));
      } catch (ImportRowException exception) {
        errors.add(new InventoryImportResult.RowError(i + 1, exception.getMessage(), raw));
      } catch (RuntimeException exception) {
        errors.add(new InventoryImportResult.RowError(i + 1, "등록 실패: " + exception.getMessage(), raw));
      }
    }
    return new InventoryImportResult(created, errors);
  }

  private InventoryItemForm toForm(List<String> f, InventoryCategoryRule.CategoryIndex categories) {
    String name = at(f, 0);
    if (name == null || name.isBlank()) {
      throw new ImportRowException("이름이 비어 있습니다.");
    }
    Classified classified = classify(at(f, 1), trimOrNull(at(f, 2)), categories);
    InventoryItemType type = classified.type();
    // ⚠️ 입력이 아니라 **해석된 경로**를 저장한다. "모니터"처럼 짧게 적힌 것을 그대로 두면
    // 분류 필터에 안 걸리는, 고치려던 바로 그 상태가 된다.
    String category = classified.path();
    String serialNo = trimOrNull(at(f, 3));
    LocalDate expiresAt = parseDate(at(f, 4), "만료일");
    LocalDate purchaseDate = parseDate(at(f, 5), "구매일");
    LocalDate warrantyEnds = parseDate(at(f, 6), "보증만료");
    LocalDate leaseEnds = parseDate(at(f, 7), "리스만료");
    String note = trimOrNull(at(f, 8));
    return new InventoryItemForm(
        name.trim(), type, category, serialNo, expiresAt, purchaseDate, warrantyEnds, leaseEnds, note);
  }

  /**
   * 유형을 정한다. <b>분류가 있으면 유형은 분류에서 나온다</b> — 화면 등록과 같은 규칙이다.
   *
   * <p>유형 열에 다른 값이 적혀 있으면 그 행을 거부한다. 조용히 덮어쓰지 않는 이유: 작성자가
   * 분류를 잘못 적었는지 유형을 잘못 적었는지 우리가 알 수 없고, 어느 쪽이든 고쳐서 다시
   * 올리는 편이 모순된 자산이 장부에 남는 것보다 낫다.
   *
   * <p>분류가 비어 있으면 전처럼 유형 열만 본다(비면 실물). 분류를 필수로 바꾸지 않은 것은
   * 이미 쓰고 있는 CSV 를 깨지 않기 위해서다.
   */
  private Classified classify(
      String typeColumn, String category, InventoryCategoryRule.CategoryIndex categories) {
    if (category == null) {
      return new Classified(parseType(typeColumn), null);
    }
    InventoryCategoryRule.Resolution resolved = categoryRule.resolve(category, categories);
    if (!resolved.ok()) {
      throw new ImportRowException(resolved.error() + " (분류: " + category + ")");
    }
    if (typeColumn != null && !typeColumn.isBlank()) {
      InventoryItemType declared = parseType(typeColumn);
      if (declared != resolved.type()) {
        throw new ImportRowException("유형과 분류가 맞지 않습니다 — 유형 " + declared.getLabel()
            + ", 분류 " + resolved.path() + "(" + resolved.type().getLabel()
            + "). 유형 열은 비워도 됩니다.");
      }
    }
    return new Classified(resolved.type(), resolved.path());
  }

  /** 한 행의 유형과 저장할 분류 경로. 분류가 비어 있으면 {@code path} 는 null 이다. */
  private record Classified(InventoryItemType type, String path) {}

  private InventoryItemType parseType(String value) {
    if (value == null || value.isBlank()) {
      return InventoryItemType.PHYSICAL; // 기본: 실물
    }
    String v = value.trim();
    for (InventoryItemType t : InventoryItemType.values()) {
      if (t.name().equalsIgnoreCase(v) || t.getLabel().equals(v)) {
        return t;
      }
    }
    throw new ImportRowException("알 수 없는 유형: " + value + " (PHYSICAL/실물 또는 SOFTWARE/SW)");
  }

  private LocalDate parseDate(String value, String field) {
    String v = trimOrNull(value);
    if (v == null) {
      return null;
    }
    try {
      return LocalDate.parse(v);
    } catch (DateTimeParseException exception) {
      throw new ImportRowException(field + " 날짜 형식 오류(yyyy-MM-dd): " + value);
    }
  }

  private static String at(List<String> fields, int index) {
    return index < fields.size() ? fields.get(index) : null;
  }

  private static String trimOrNull(String value) {
    if (value == null) {
      return null;
    }
    String v = value.trim();
    return v.isEmpty() ? null : v;
  }

  /** 간이 CSV 파서(RFC4180 큰따옴표 인용/이스케이프 지원). */
  static List<String> parseCsvLine(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (inQuotes) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            sb.append('"');
            i++;
          } else {
            inQuotes = false;
          }
        } else {
          sb.append(c);
        }
      } else if (c == '"') {
        inQuotes = true;
      } else if (c == ',') {
        out.add(sb.toString());
        sb.setLength(0);
      } else {
        sb.append(c);
      }
    }
    out.add(sb.toString());
    return out;
  }

  private static class ImportRowException extends RuntimeException {
    ImportRowException(String message) {
      super(message);
    }
  }
}
