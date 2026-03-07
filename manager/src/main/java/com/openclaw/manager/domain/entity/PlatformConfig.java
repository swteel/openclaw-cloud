package com.openclaw.manager.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "platform_config")
public class PlatformConfig {

    @Id
    @Column(name = "config_key", nullable = false, length = 128)
    private String key;

    @Column(name = "config_value", length = 1024)
    private String value;

    public PlatformConfig() {}

    public PlatformConfig(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
