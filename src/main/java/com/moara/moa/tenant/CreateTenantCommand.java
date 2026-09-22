package com.moara.moa.tenant;

/** 테넌트 생성 명령. code는 유니크 식별 키, name은 표시명. */
public record CreateTenantCommand(String name, String code) {}
