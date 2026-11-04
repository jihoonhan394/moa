package com.moara.moa.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * DB(timestamptz)에서 읽은 시각을 한국 시각(+09:00)으로 바꾼다.
 *
 * <p>Postgres는 timestamptz에 오프셋을 저장하지 않아 읽으면 UTC로 온다. 화면(Thymeleaf
 * {@code #temporals.format})은 받은 값을 그대로 찍으므로 모든 시각이 9시간 이르게 보였다.
 * 순간(instant)은 그대로이고 저장할 때는 손대지 않는다.
 */
@Converter(autoApply = true)
public class KoreanTimeConverter implements AttributeConverter<OffsetDateTime, OffsetDateTime> {
  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  @Override
  public OffsetDateTime convertToDatabaseColumn(OffsetDateTime attribute) {
    return attribute;
  }

  @Override
  public OffsetDateTime convertToEntityAttribute(OffsetDateTime dbData) {
    return dbData == null ? null : dbData.atZoneSameInstant(ZONE).toOffsetDateTime();
  }
}
