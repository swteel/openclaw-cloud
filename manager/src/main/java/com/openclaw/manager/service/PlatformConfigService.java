package com.openclaw.manager.service;

import com.openclaw.manager.domain.entity.PlatformConfig;
import com.openclaw.manager.domain.repository.PlatformConfigRepository;
import org.springframework.stereotype.Service;

@Service
public class PlatformConfigService {

    private final PlatformConfigRepository repo;

    public PlatformConfigService(PlatformConfigRepository repo) {
        this.repo = repo;
    }

    public String get(String key) {
        return repo.findById(key).map(PlatformConfig::getValue).orElse(null);
    }

    public String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    public void set(String key, String value) {
        repo.save(new PlatformConfig(key, value));
    }
}
