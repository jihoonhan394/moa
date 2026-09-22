package com.moara.moa.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 기관(테넌트) 생성 화면 입력. code는 로그인 1단계에서 입력하는 기관 식별 코드다. */
public record TenantForm(
    @NotBlank @Size(max = 100) String name,
    @NotBlank
        @Size(max = 50)
        @Pattern(
            regexp = "[A-Za-z0-9._-]+",
            message = "기관 코드는 영문/숫자/._- 만 사용할 수 있습니다.")
        String code) {}
