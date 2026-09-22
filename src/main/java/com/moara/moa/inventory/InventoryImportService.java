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
 */
@Service
public class InventoryImportService {
  private final InventoryItemService inventoryService;

  public InventoryImportService(InventoryItemService inventoryService) {
    this.inventoryService = inventoryService;
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
        InventoryItemForm form = toForm(fields);
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

  private InventoryItemForm toForm(List<String> f) {
    String name = at(f, 0);
    if (name == null || name.isBlank()) {
      throw new ImportRowException("이름이 비어 있습니다.");
    }
    InventoryItemType type = parseType(at(f, 1));
    String category = trimOrNull(at(f, 2));
    String serialNo = trimOrNull(at(f, 3));
    LocalDate expiresAt = parseDate(at(f, 4), "만료일");
    LocalDate purchaseDate = parseDate(at(f, 5), "구매일");
    LocalDate warrantyEnds = parseDate(at(f, 6), "보증만료");
    LocalDate leaseEnds = parseDate(at(f, 7), "리스만료");
    String note = trimOrNull(at(f, 8));
    return new InventoryItemForm(
        name.trim(), type, category, serialNo, expiresAt, purchaseDate, warrantyEnds, leaseEnds, note);
  }

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
