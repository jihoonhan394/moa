package com.moara.moa.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagedUserRepository extends JpaRepository<ManagedUser, UUID> {
  List<ManagedUser> findAllByOrderByNameAsc();

  /** 다중역할: 해당 역할을 가진 사용자 전체(user_roles 조인). */
  /**
   * 사용자 목록의 검색·필터. 거르기를 DB 가 한다.
   *
   * <p>역할은 {@code @ElementCollection} 이라 조인이 필요하고, 그러면 역할이 여러 개인
   * 사람이 중복으로 나온다 — {@code distinct} 로 접는다.
   *
   * @param term 이름·아이디·이메일에서 찾을 말. {@code null} 이면 전체
   * @param status 이 상태만. {@code null} 이면 전체
   * @param role 이 역할을 가진 사람만. {@code null} 이면 전체
   */
  @Query("""
      select distinct u from ManagedUser u
      left join u.roles r
      where u.tenantId = :tenantId
        and (:status is null or u.status = :status)
        and (:role is null or r = :role)
        and (:term is null
             or lower(u.name) like lower(concat('%', :term, '%')) escape '!'
             or lower(u.username) like lower(concat('%', :term, '%')) escape '!'
             or lower(u.email) like lower(concat('%', :term, '%')) escape '!')
      order by u.name asc
      """)
  List<ManagedUser> filter(
      @Param("tenantId") UUID tenantId,
      @Param("term") String term,
      @Param("status") UserStatus status,
      @Param("role") UserRole role);

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
