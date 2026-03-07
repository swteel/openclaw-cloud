package com.openclaw.manager.domain.repository;

import com.openclaw.manager.domain.entity.PlatformConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformConfigRepository extends JpaRepository<PlatformConfig, String> {
}
