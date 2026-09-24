package com.moara.moa.consumable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumableItemRepository extends JpaRepository<ConsumableItem, UUID> {
  List<ConsumableItem> findByTenantIdOrderByNameAsc(UUID tenantId);

  List<ConsumableItem> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);

  Optional<ConsumableItem> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<ConsumableItem> findByTenantIdAndName(UUID tenantId, String name);
}
