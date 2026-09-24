package com.moara.moa.user;

import com.moara.moa.tenant.Tenant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ManagedUserService {
  private final ManagedUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public ManagedUserService(ManagedUserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public List<ManagedUser> findAll() {
    return userRepository.findAllByOrderByNameAsc();
  }

  public List<ManagedUser> findByTenant(UUID tenantId) {
    return userRepository.findAllByTenantIdOrderByNameAsc(tenantId);
  }

  public long countUsers() {
    return userRepository.count();
  }

  /**
   * 테넌트 무관 전역 조회. <b>플랫폼(SYSTEM_ADMIN) 콘솔과 부트스트랩 전용</b>이며, 호출부가 스스로
   * 소속을 검증해야 한다(예: {@code TenantController.requireTenantAdmin}). 기관 스코프 경로에서는
   * 반드시 {@link #findById(UUID, UUID)}를 쓴다 — 교차 테넌트 접근이 열린다.
   */
  public ManagedUser findById(UUID id) {
    return userRepository.findById(id).orElseThrow(() -> new ManagedUserNotFoundException(id));
  }

  /**
   * 기관 스코프 단건 조회. 대상이 해당 기관 소속이 아니면 존재 여부조차 노출하지 않고
   * {@link ManagedUserNotFoundException}(404)을 던진다(AGENTS.md 멀티테넌트 불변식).
   */
  public ManagedUser findById(UUID tenantId, UUID id) {
    return userRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ManagedUserNotFoundException(id));
  }

  /** 기본 테넌트(MOA)에 사용자를 생성한다(테스트/부트스트랩 경로). */
  @Transactional
  public ManagedUser create(UserForm form) {
    return create(Tenant.DEFAULT_TENANT_ID, form);
  }

  /** 지정한 기관(테넌트)에 <b>일반 사용자(USER)</b>를 생성한다. 유일성은 기관 범위로 검사한다. */
  @Transactional
  public ManagedUser create(UUID tenantId, UserForm form) {
    return create(tenantId, form, UserRole.USER);
  }

  /** 지정한 기관에 단일 역할로 사용자를 생성한다(편의). */
  @Transactional
  public ManagedUser create(UUID tenantId, UserForm form, UserRole role) {
    return create(tenantId, form, Set.of(role));
  }

  /**
   * 지정한 기관에 사용자를 생성한다(다중역할). 기관 관리자가 부여 가능한 역할은 기관 스코프
   * (일반 사용자·기관 관리자·자원 관리자)뿐 — SYSTEM_ADMIN(플랫폼)은 이 경로로 부여할 수 없다.
   */
  @Transactional
  public ManagedUser create(UUID tenantId, UserForm form, Set<UserRole> roles) {
    validatePassword(form.password());
    validateDuplicates(tenantId, form);
    OffsetDateTime now = OffsetDateTime.now();
    ManagedUser user = new ManagedUser(
        UUID.randomUUID(), tenantId, UserRole.USER, form, passwordEncoder.encode(form.password()), now);
    user.setRoles(assignable(roles), now);
    return userRepository.save(user);
  }

  /**
   * 사용자의 역할 집합을 교체한다(다중역할, 체크박스). 부여 역할은 기관 스코프로 한정하며,
   * 대상이 플랫폼 운영자(SYSTEM_ADMIN)면 이 경로로 바꾸지 않는다(플랫폼 계정 보호).
   */
  @Transactional
  public void assignRoles(UUID tenantId, UUID id, Set<UserRole> roles) {
    ManagedUser user = findById(tenantId, id);
    if (user.hasRole(UserRole.SYSTEM_ADMIN)) {
      return;
    }
    user.setRoles(assignable(roles), OffsetDateTime.now());
  }

  /**
   * 기관 관리자가 부여 가능한 역할로 정제한다: SYSTEM_ADMIN 제거(플랫폼 전용), 관리 역할이 있으면
   * USER 제거(관리자는 일반 사용자 아님), 빈 집합은 일반 사용자({USER}).
   */
  private Set<UserRole> assignable(Set<UserRole> roles) {
    Set<UserRole> out = roles == null ? new HashSet<>()
        : roles.stream()
            .filter(r -> r == UserRole.USER || r == UserRole.TENANT_ADMIN
                || r == UserRole.INFRA_MANAGER || r == UserRole.ASSET_MANAGER)
            .collect(Collectors.toCollection(HashSet::new));
    if (out.contains(UserRole.TENANT_ADMIN) || out.contains(UserRole.INFRA_MANAGER)
        || out.contains(UserRole.ASSET_MANAGER)) {
      out.remove(UserRole.USER);
    }
    if (out.isEmpty()) {
      out.add(UserRole.USER);
    }
    return out;
  }

  /**
   * 기관 자가 회원가입 신청을 접수한다. 지정한 기관에 <b>USER + PENDING(승인 대기)</b> 상태로 생성하며,
   * 유일성은 기관 범위로 검사한다. 승인(활성화) 전까지는 로그인할 수 없다.
   */
  @Transactional
  public ManagedUser register(UUID tenantId, SignupForm form) {
    UserForm userForm = new UserForm(
        form.username(), form.name(), form.email(), form.phone(), form.password(), UserStatus.PENDING);
    return create(tenantId, userForm);
  }

  /** 승인 대기(PENDING) 사용자를 활성화한다. 기관 관리자/플랫폼 운영자만 호출한다. */
  @Transactional
  public ManagedUser approve(UUID tenantId, UUID id) {
    ManagedUser user = findById(tenantId, id);
    // 상태만 전환한다(연락처 등 다른 필드 불변, email/phone null인 계정에서도 안전).
    user.changeStatus(UserStatus.ACTIVE, OffsetDateTime.now());
    return user;
  }

  /**
   * SYSTEM_ADMIN 부트스트랩 계정을 보장한다(멱등). 없으면 생성하고, 있으면 주입된 비밀번호로 동기화한다.
   * 비밀번호는 env(`MOA_BOOTSTRAP_ADMIN_PASSWORD`)로만 주입되며 코드/DB 평문에 남기지 않는다.
   */
  @Transactional
  public ManagedUser ensureSystemAdmin(String username, String rawPassword) {
    validatePassword(rawPassword);
    OffsetDateTime now = OffsetDateTime.now();
    String hash = passwordEncoder.encode(rawPassword);
    // SYSTEM_ADMIN은 tenant_id IS NULL 풀에 속한다. 과거엔 전역 findByUsernameIgnoreCase로 조회했는데,
    // V16(기관별 아이디 유일성) 이후 어떤 기관에 같은 아이디('admin')가 생기면 전역 조회가 다건→
    // NonUniqueResultException으로 앱 기동이 실패했다. 플랫폼 풀로만 조회하고, 혹시 다건이어도
    // 첫 행을 동기화해 절대 크래시하지 않도록 한다.
    List<ManagedUser> platformAdmins = userRepository.findAllByTenantIdIsNullAndUsernameIgnoreCase(username);
    if (!platformAdmins.isEmpty()) {
      ManagedUser existing = platformAdmins.get(0);
      existing.changePassword(hash, now);
      if (!existing.hasRole(UserRole.SYSTEM_ADMIN)) {
        existing.changeRole(UserRole.SYSTEM_ADMIN, now);
      }
      return existing;
    }
    // 부트스트랩 계정은 env로만 주입되며 연락처를 받지 않는다(email/phone null). 운영자 UI 생성은 필수.
    return userRepository.save(
        ManagedUser.systemAdmin(UUID.randomUUID(), username, "시스템 관리자", null, null, hash, now));
  }

  /**
   * 기관 대표 관리자(TENANT_ADMIN)의 이름·이메일·전화·비밀번호를 수정한다. 아이디·상태·역할은 불변.
   * 이메일은 기관 범위에서 유일해야 하며, 비밀번호는 입력했을 때만(공백 아님) 재설정한다.
   */
  @Transactional
  public ManagedUser updateTenantAdmin(UUID id, String name, String email, String phone, String rawPassword) {
    if (name == null || name.isBlank() || email == null || email.isBlank()
        || phone == null || phone.isBlank()) {
      throw new IllegalArgumentException("name/email/phone required");
    }
    ManagedUser user = findById(id);
    if (userRepository.existsByTenantIdAndEmailIgnoreCaseAndIdNot(user.getTenantId(), email.trim(), id)) {
      throw new DuplicateManagedUserException("email");
    }
    OffsetDateTime now = OffsetDateTime.now();
    user.changeProfile(name, email, phone, now);
    if (rawPassword != null && !rawPassword.isBlank()) {
      validatePassword(rawPassword);
      user.changePassword(passwordEncoder.encode(rawPassword), now);
    }
    return user;
  }

  /** 사용자를 활성(ACTIVE)으로 전환한다. */
  @Transactional
  public void activate(UUID tenantId, UUID id) {
    findById(tenantId, id).changeStatus(UserStatus.ACTIVE, OffsetDateTime.now());
  }

  /** 사용자를 비활성(DISABLED)으로 전환한다(로그인 차단). */
  @Transactional
  public void deactivate(UUID tenantId, UUID id) {
    findById(tenantId, id).changeStatus(UserStatus.DISABLED, OffsetDateTime.now());
  }

  /**
   * 사용자를 영구 삭제한다. 활동 이력(감사 로그·세션·그룹/권한 등)이 남아 FK로 참조되면
   * 삭제할 수 없으며, 이 경우 {@link org.springframework.dao.DataIntegrityViolationException}가
   * 즉시 발생하도록 flush 한다(호출부에서 '비활성' 안내로 전환). 참조가 없으면 삭제된다.
   */
  @Transactional
  public void delete(UUID id) {
    ManagedUser user = findById(id);
    userRepository.delete(user);
    userRepository.flush();
  }

  /** 특정 기관의 대표 관리자(TENANT_ADMIN)를 생성한다(연락처 필수). 플랫폼 콘솔의 기관 상세에서 프로비저닝. */
  @Transactional
  public ManagedUser createTenantAdmin(
      UUID tenantId, String username, String name, String email, String phone, String rawPassword) {
    validatePassword(rawPassword);
    String trimmed = username.trim();
    // 유일 키는 이메일. username은 표시 이름이라 중복 허용(동명이인).
    if (email != null && !email.isBlank()
        && userRepository.existsByTenantIdAndEmailIgnoreCase(tenantId, email.trim())) {
      throw new DuplicateManagedUserException("email");
    }
    OffsetDateTime now = OffsetDateTime.now();
    return userRepository.save(ManagedUser.tenantAdmin(
        UUID.randomUUID(), tenantId, trimmed, name, email, phone,
        passwordEncoder.encode(rawPassword), now));
  }

  /** 특정 기관의 사용자 수(대표 관리자 프로비저닝/좌석 표시용). */
  public long countByTenant(UUID tenantId) {
    return userRepository.findAllByTenantIdOrderByNameAsc(tenantId).size();
  }

  /** 특정 기관의 대표 관리자(TENANT_ADMIN) 목록. */
  public List<ManagedUser> findTenantAdmins(UUID tenantId) {
    return userRepository.findAllByTenantIdOrderByNameAsc(tenantId).stream()
        .filter(user -> user.hasRole(UserRole.TENANT_ADMIN))
        .toList();
  }

  /** 플랫폼 운영자(SYSTEM_ADMIN) 목록. */
  public List<ManagedUser> findOperators() {
    return userRepository.findAllByRole(UserRole.SYSTEM_ADMIN);
  }

  /** 특정 역할의 사용자 전체(스코프 무관). 메일 수신자 산정 등에 쓴다. */
  public List<ManagedUser> findByRole(UserRole role) {
    return userRepository.findAllByRole(role);
  }

  /**
   * 기관 발송 대상 이메일: 해당 기관의 이메일이 등록된 <b>활성 구성원 전체</b>.
   * 공지 이메일 브로드캐스트와 기관 메일 '전체 발송'의 공용 수신자 규칙(중복 방지 위해 한 곳에서 관리).
   *
   * <p>과거에는 {@code hasRole(USER)}로 걸렀으나, {@link #assignable}이 "관리 역할이 있으면 USER를
   * 제거"하므로 <b>관리자가 자기 기관 공지·전체 메일을 못 받는</b> 결과가 됐다(회피 불가 — USER를 수동으로
   * 줘도 저장 시 제거됨). 역할은 권한 구분이지 "사람 분류"가 아니므로, 수신자는 기관 구성원 전체로 본다.
   * 플랫폼 운영자(SYSTEM_ADMIN)는 기관 소속이 아니라 {@code findByTenant}에 애초에 포함되지 않지만,
   * 방어적으로 한 번 더 제외한다.
   */
  public List<String> activeUserEmails(UUID tenantId) {
    List<String> emails = new ArrayList<>();
    for (ManagedUser user : findByTenant(tenantId)) {
      if (!user.hasRole(UserRole.SYSTEM_ADMIN)
          && user.getStatus() == UserStatus.ACTIVE
          && user.getEmail() != null && !user.getEmail().isBlank()) {
        emails.add(user.getEmail());
      }
    }
    return emails;
  }

  /** 플랫폼 운영자(SYSTEM_ADMIN) 계정 생성(연락처 필수). 테넌트에 속하지 않으며 아이디는 운영자 풀에서 유일해야 한다. */
  @Transactional
  public ManagedUser createOperator(
      String username, String name, String email, String phone, String rawPassword) {
    validatePassword(rawPassword);
    String trimmed = username.trim();
    if (userRepository.findByTenantIdIsNullAndUsernameIgnoreCase(trimmed).isPresent()) {
      throw new DuplicateManagedUserException("username");
    }
    OffsetDateTime now = OffsetDateTime.now();
    return userRepository.save(ManagedUser.systemAdmin(
        UUID.randomUUID(), trimmed, name.trim(), email, phone, passwordEncoder.encode(rawPassword), now));
  }

  @Transactional
  public ManagedUser update(UUID tenantId, UUID id, UserForm form) {
    ManagedUser user = findById(tenantId, id);
    validateDuplicates(user.getTenantId(), form, id);
    user.update(form, OffsetDateTime.now());
    if (form.password() != null && !form.password().isBlank()) {
      validatePassword(form.password());
      user.changePassword(passwordEncoder.encode(form.password()), OffsetDateTime.now());
    }
    return user;
  }

  @Transactional
  public void disable(UUID tenantId, UUID id) {
    findById(tenantId, id).changeStatus(UserStatus.DISABLED, OffsetDateTime.now());
  }

  /**
   * 플랫폼 운영자(SYSTEM_ADMIN, {@code tenant_id IS NULL}) 비활성화. 기관 스코프가 없는 대상이라
   * 별도 경로를 두되, <b>운영자가 아닌 계정은 거부</b>해 기관 사용자로 넘어가지 않게 한다.
   */
  @Transactional
  public void disableOperator(UUID id) {
    ManagedUser user = findById(id);
    if (user.getTenantId() != null || !user.hasRole(UserRole.SYSTEM_ADMIN)) {
      throw new ManagedUserNotFoundException(id);
    }
    user.changeStatus(UserStatus.DISABLED, OffsetDateTime.now());
  }

  private void validateDuplicates(UUID tenantId, UserForm form) {
    // 유일 키는 이메일(로그인 ID). username은 표시 이름이라 중복 가능.
    if (hasEmail(form)
        && userRepository.existsByTenantIdAndEmailIgnoreCase(tenantId, form.email())) {
      throw new DuplicateManagedUserException("email");
    }
  }

  private void validateDuplicates(UUID tenantId, UserForm form, UUID id) {
    // 유일 키는 이메일(로그인 ID). username은 표시 이름이라 중복 가능.
    if (hasEmail(form)
        && userRepository.existsByTenantIdAndEmailIgnoreCaseAndIdNot(tenantId, form.email(), id)) {
      throw new DuplicateManagedUserException("email");
    }
  }

  private boolean hasEmail(UserForm form) {
    return form.email() != null && !form.email().isBlank();
  }

  private void validatePassword(String password) {
    if (password == null || password.length() < 8) {
      throw new IllegalArgumentException("Password must contain at least 8 characters.");
    }
  }
}
