package com.moara.moa.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ManagedUserRepository extends JpaRepository<ManagedUser, UUID> {
  List<ManagedUser> findAllByOrderByNameAsc();

  /** 다중역할: 해당 역할을 가진 사용자 전체(user_roles 조인). */
  @Query("SELECT u FROM ManagedUser u JOIN u.roles r WHERE r = :role ORDER BY u.name ASC")
  List<ManagedUser> findAllByRole(UserRole role);

  List<ManagedUser> findAllByTenantIdOrderByNameAsc(UUID tenantId);

  /** 기관 스코프 단건 조회 — 교차 테넌트 접근 차단용(AGENTS.md 멀티테넌트 불변식). */
  Optional<ManagedUser> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<ManagedUser> findByUsernameIgnoreCase(String username);

  boolean existsByUsernameIgnoreCase(String username);

  boolean existsByEmailIgnoreCase(String email);

  boolean existsByUsernameIgnoreCaseAndIdNot(String username, UUID id);

  boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

  // 기관(테넌트)별 조회/유일성 검사 — 두레이식 진입(기관+아이디)용.
  Optional<ManagedUser> findByTenantIdAndUsernameIgnoreCase(UUID tenantId, String username);

  /** 기관 내 로그인 조회 — 이메일이 기관 내 유일 키(로그인 ID). */
  Optional<ManagedUser> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

  Optional<ManagedUser> findByTenantIdIsNullAndUsernameIgnoreCase(String username);

  // 플랫폼(tenant_id IS NULL) 풀의 동일 아이디 전체. 부트스트랩이 기관 스코프의 같은 아이디에
  // 흔들리지 않도록 다건이어도 리스트로 받아 안전하게 처리한다.
  List<ManagedUser> findAllByTenantIdIsNullAndUsernameIgnoreCase(String username);

  boolean existsByTenantIdAndUsernameIgnoreCase(UUID tenantId, String username);

  boolean existsByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

  boolean existsByTenantIdAndUsernameIgnoreCaseAndIdNot(UUID tenantId, String username, UUID id);

  boolean existsByTenantIdAndEmailIgnoreCaseAndIdNot(UUID tenantId, String email, UUID id);
}
