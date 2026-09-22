package com.moara.moa.ai;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSettingRepository extends JpaRepository<AiSetting, UUID> {
  Optional<AiSetting> findByTenantId(UUID tenantId);
}
