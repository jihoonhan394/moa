package com.moara.moa.audit;

/** 감사 행위의 범위. GLOBAL은 SYSTEM_ADMIN의 전역 행위(tenant_id 없음), TENANT는 테넌트 내 행위. */
public enum AuditActionScope {
  GLOBAL,
  TENANT
}
