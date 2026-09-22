package com.moara.moa.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "managed_users")
public class ManagedUser {
  @Id private UUID id;

  // SYSTEM_ADMIN은 특정 테넌트에 속하지 않으므로 null 허용.
  @Column(name = "tenant_id")
  private UUID tenantId;

  // 유일성은 기관별 복합 UNIQUE(tenant_id, username)로 DB에서 강제한다(V16). 단일 컬럼 UNIQUE 아님.
  @Column(nullable = false, length = 50)
  private String username;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(nullable = false, length = 100)
  private String name;

  // 선택값. 유일성은 기관별 복합 UNIQUE(tenant_id, email)로 DB에서 강제한다(V16).
  @Column(length = 255)
  private String email;

  // 연락 대응(메일/전화)용. DB는 nullable(기존 계정 보존), 신규 생성/수정은 폼에서 필수화(V22).
  @Column(length = 30)
  private String phone;

  // 다중역할(capability). 일반 사용자={USER}, 관리자={TENANT_ADMIN/ASSET_MANAGER 조합}, 플랫폼={SYSTEM_ADMIN}.
  // 한 계정이 여러 관리 권한을 동시에 가질 수 있다(작은 회사는 한 명이 다, 크면 팀별 분리). V24.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private Set<UserRole> roles = new HashSet<>();

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserStatus status;

  @Column(name = "last_login_at")
  private OffsetDateTime lastLoginAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected ManagedUser() {}

  ManagedUser(
      UUID id, UUID tenantId, UserRole role, UserForm form, String passwordHash, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.roles = new HashSet<>(Set.of(role));
    this.passwordHash = passwordHash;
    update(form, now);
    this.createdAt = now;
  }

  /**
   * SYSTEM_ADMIN 부트스트랩 계정 생성용 팩토리. 특정 테넌트에 속하지 않으며(email/tenant null),
   * 일반 사용자 생성 경로(UserForm)와 달리 email을 요구하지 않는다.
   */
  static ManagedUser systemAdmin(
      UUID id, String username, String name, String email, String phone,
      String passwordHash, OffsetDateTime now) {
    ManagedUser user = new ManagedUser();
    user.id = id;
    user.tenantId = null;
    user.roles = new HashSet<>(Set.of(UserRole.SYSTEM_ADMIN));
    user.username = username.trim();
    user.name = name.trim();
    user.email = normalizeEmail(email);
    user.phone = normalizePhone(phone);
    user.passwordHash = passwordHash;
    user.status = UserStatus.ACTIVE;
    user.createdAt = now;
    user.updatedAt = now;
    return user;
  }

  /**
   * 기관 대표 관리자(TENANT_ADMIN) 프로비저닝용 팩토리. 플랫폼 콘솔의 기관 상세에서 생성한다.
   * email은 요구하지 않으며(널), 아이디는 해당 기관 범위에서 유일해야 한다.
   */
  static ManagedUser tenantAdmin(
      UUID id, UUID tenantId, String username, String name, String email, String phone,
      String passwordHash, OffsetDateTime now) {
    ManagedUser user = new ManagedUser();
    user.id = id;
    user.tenantId = tenantId;
    user.roles = new HashSet<>(Set.of(UserRole.TENANT_ADMIN));
    user.username = username.trim();
    user.name = name.trim();
    user.email = normalizeEmail(email);
    user.phone = normalizePhone(phone);
    user.passwordHash = passwordHash;
    user.status = UserStatus.ACTIVE;
    user.createdAt = now;
    user.updatedAt = now;
    return user;
  }

  void update(UserForm form, OffsetDateTime now) {
    this.username = form.username().trim();
    this.name = form.name().trim();
    this.email = normalizeEmail(form.email());
    // phone은 값이 있을 때만 반영(프로그램 생성/승인 등 미입력 경로에서 기존 값을 지우지 않음).
    if (form.phone() != null && !form.phone().isBlank()) {
      this.phone = normalizePhone(form.phone());
    }
    this.status = form.status();
    this.updatedAt = now;
  }

  /** 연락처(이름/이메일/전화)를 갱신한다. 관리자 편집에서 상태/역할은 건드리지 않고 연락정보만 바꾼다. */
  void changeProfile(String name, String email, String phone, OffsetDateTime now) {
    this.name = name.trim();
    this.email = normalizeEmail(email);
    this.phone = normalizePhone(phone);
    this.updatedAt = now;
  }

  private static String normalizeEmail(String email) {
    return email == null || email.isBlank() ? null : email.trim().toLowerCase();
  }

  /** 전화번호는 <b>숫자만</b> 저장한다(입력이 010-1234-5678이든 01012345678이든 동일하게 정규화). */
  private static String normalizePhone(String phone) {
    if (phone == null) {
      return null;
    }
    String digits = phone.replaceAll("[^0-9]", "");
    return digits.isEmpty() ? null : digits;
  }

  /** 표시용 하이픈 포맷(예: 01030947612 → 010-3094-7612). 규칙 밖이면 저장값 그대로. */
  public String getPhoneDisplay() {
    return formatPhone(phone);
  }

  static String formatPhone(String digits) {
    if (digits == null || digits.isBlank()) {
      return digits;
    }
    return switch (digits.length()) {
      case 11 -> digits.replaceFirst("(\\d{3})(\\d{4})(\\d{4})", "$1-$2-$3");           // 010-3094-7612
      case 10 -> digits.startsWith("02")
          ? digits.replaceFirst("(\\d{2})(\\d{4})(\\d{4})", "$1-$2-$3")                 // 02-1234-5678
          : digits.replaceFirst("(\\d{3})(\\d{3})(\\d{4})", "$1-$2-$3");                // 010-123-4567
      case 9 -> digits.replaceFirst("(\\d{2})(\\d{3})(\\d{4})", "$1-$2-$3");            // 02-123-4567
      case 8 -> digits.replaceFirst("(\\d{4})(\\d{4})", "$1-$2");                       // 1234-5678
      default -> digits;
    };
  }

  void changePassword(String passwordHash, OffsetDateTime now) {
    this.passwordHash = passwordHash;
    this.updatedAt = now;
  }

  /** 이름만 변경한다(아이디/이메일/상태 불변). email이 null일 수 있는 관리자 편집에 안전. */
  void changeName(String name, OffsetDateTime now) {
    this.name = name.trim();
    this.updatedAt = now;
  }

  /** 상태만 변경한다(email 등 다른 필드 불변). 활성/비활성 전환에 쓴다. */
  void changeStatus(UserStatus status, OffsetDateTime now) {
    this.status = status;
    this.updatedAt = now;
  }

  /** 역할 집합을 통째로 교체한다(다중역할). 비어 있으면 일반 사용자({USER})로 둔다. */
  public void setRoles(Set<UserRole> roles, OffsetDateTime now) {
    this.roles = (roles == null || roles.isEmpty())
        ? new HashSet<>(Set.of(UserRole.USER)) : new HashSet<>(roles);
    this.updatedAt = now;
  }

  /** 단일 역할로 교체한다(기존 역할 대체). 부트스트랩·강등 등에 쓴다. */
  public void changeRole(UserRole role, OffsetDateTime now) {
    this.roles = new HashSet<>(Set.of(role));
    this.updatedAt = now;
  }

  public boolean hasRole(UserRole role) {
    return roles.contains(role);
  }

  /** 관리 역할(TENANT_ADMIN/INFRA_MANAGER/ASSET_MANAGER)을 하나라도 가지면 true. */
  public boolean isTenantManager() {
    return roles.contains(UserRole.TENANT_ADMIN)
        || roles.contains(UserRole.INFRA_MANAGER)
        || roles.contains(UserRole.ASSET_MANAGER);
  }

  /** 우선순위 대표 역할(착지/기본 표시용). */
  public UserRole primaryRole() {
    for (UserRole r : List.of(UserRole.SYSTEM_ADMIN, UserRole.TENANT_ADMIN,
        UserRole.INFRA_MANAGER, UserRole.ASSET_MANAGER, UserRole.USER)) {
      if (roles.contains(r)) {
        return r;
      }
    }
    return UserRole.USER;
  }

  /** 화면 표시용 역할 요약(관리 역할 라벨들, 없으면 일반 사용자). */
  public String getRoleSummary() {
    List<String> labels = new ArrayList<>();
    for (UserRole r : List.of(UserRole.SYSTEM_ADMIN, UserRole.TENANT_ADMIN,
        UserRole.INFRA_MANAGER, UserRole.ASSET_MANAGER)) {
      if (roles.contains(r)) {
        labels.add(r.getLabel());
      }
    }
    return labels.isEmpty() ? UserRole.USER.getLabel() : String.join(", ", labels);
  }

  public void recordLogin(OffsetDateTime now) {
    this.lastLoginAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public String getUsername() { return username; }
  public String getPasswordHash() { return passwordHash; }
  public String getName() { return name; }
  public String getEmail() { return email; }
  public String getPhone() { return phone; }
  public Set<UserRole> getRoles() { return roles; }
  public UserStatus getStatus() { return status; }
  public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
