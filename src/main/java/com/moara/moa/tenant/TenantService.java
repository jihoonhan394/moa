package com.moara.moa.tenant;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TenantService {
  private final TenantRepository tenantRepository;
  private final Clock clock;

  @Autowired
  public TenantService(TenantRepository tenantRepository) {
    this(tenantRepository, Clock.systemUTC());
  }

  TenantService(TenantRepository tenantRepository, Clock clock) {
    this.tenantRepository = tenantRepository;
    this.clock = clock;
  }

  public Tenant getById(UUID id) {
    return tenantRepository.findById(id).orElseThrow(() -> new TenantNotFoundException(String.valueOf(id)));
  }

  /** 전체 기관 목록(코드 오름차순). 플랫폼 관리 콘솔용. */
  public List<Tenant> findAll() {
    return tenantRepository.findAllByOrderByCodeAsc();
  }

  public long countAll() {
    return tenantRepository.count();
  }

  public long countActive() {
    return tenantRepository.countByStatus(TenantStatus.ACTIVE);
  }

  public Tenant getByCode(String code) {
    return tenantRepository.findByCode(code).orElseThrow(() -> new TenantNotFoundException(code));
  }

  /** 기본 테넌트(code=MOA) 조회. V3 migration이 시드한 행을 반환한다. */
  public Tenant getDefaultTenant() {
    return getByCode(Tenant.DEFAULT_TENANT_CODE);
  }

  @Transactional
  public Tenant createTenant(CreateTenantCommand command) {
    String code = command.code().trim();
    if (tenantRepository.existsByCodeIgnoreCase(code)) {
      throw new DuplicateTenantException(code);
    }
    return tenantRepository.save(new Tenant(UUID.randomUUID(), command, OffsetDateTime.now(clock)));
  }

  @Transactional
  public void disableTenant(UUID tenantId) {
    getById(tenantId).disable(OffsetDateTime.now(clock));
  }

  @Transactional
  public void enableTenant(UUID tenantId) {
    getById(tenantId).enable(OffsetDateTime.now(clock));
  }

  /** 기관 상세: 구독 기간 + 사용 기능 집합을 갱신한다. */
  @Transactional
  public Tenant updateDetail(UUID id, LocalDate start, LocalDate end, Set<FeatureModule> features) {
    Tenant tenant = getById(id);
    OffsetDateTime now = OffsetDateTime.now(clock);
    tenant.updateSubscription(start, end, now);
    tenant.setFeatures(features, now);
    return tenant;
  }
}
