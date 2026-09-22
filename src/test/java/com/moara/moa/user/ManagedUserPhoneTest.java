package com.moara.moa.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 전화번호: DB엔 숫자만 저장, 화면엔 하이픈 포맷. 입력은 어느 형식이든 동일하게 정규화. */
class ManagedUserPhoneTest {

  @Test
  void formatsKoreanNumbers() {
    assertThat(ManagedUser.formatPhone("01030947612")).isEqualTo("010-3094-7612");
    assertThat(ManagedUser.formatPhone("0212345678")).isEqualTo("02-1234-5678");
    assertThat(ManagedUser.formatPhone("021234567")).isEqualTo("02-123-4567");
    assertThat(ManagedUser.formatPhone("01012345678")).isEqualTo("010-1234-5678");
  }

  @Test
  void storesDigitsOnlyRegardlessOfInputFormat() {
    ManagedUser dashed = user("010-3094-7612");
    assertThat(dashed.getPhone()).isEqualTo("01030947612");        // DB = 숫자만
    assertThat(dashed.getPhoneDisplay()).isEqualTo("010-3094-7612"); // 화면 = 하이픈

    ManagedUser plain = user("01030947612");
    assertThat(plain.getPhone()).isEqualTo("01030947612");
    assertThat(plain.getPhoneDisplay()).isEqualTo("010-3094-7612");
  }

  private ManagedUser user(String phone) {
    UserForm form = new UserForm("한지훈", "한지훈", "a@b.com", phone, "password12", UserStatus.ACTIVE);
    return new ManagedUser(UUID.randomUUID(), UUID.randomUUID(), UserRole.USER, form, "hash", OffsetDateTime.now());
  }
}
