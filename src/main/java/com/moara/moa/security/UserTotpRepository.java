package com.moara.moa.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTotpRepository extends JpaRepository<UserTotp, UUID> {
  Optional<UserTotp> findByUserId(UUID userId);
}
