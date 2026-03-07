package com.openclaw.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "platform")
public class PlatformProperties {

    private int maxContainers = 100;
    private int portRangeStart = 20000;
    private int portRangeEnd = 25000;
    private String internalToken;
    private String jwtSecret;
    private long jwtExpirationMs = 86400000L; // 24h
    private String containerImage = "openclaw-platform:latest";
    private String dockerHost = "unix:///var/run/docker.sock";
    private String dashscopeApiKey = "";
    private String platformNetwork = "openclaw-platform_default";

    public int getMaxContainers() { return maxContainers; }
    public void setMaxContainers(int maxContainers) { this.maxContainers = maxContainers; }

    public int getPortRangeStart() { return portRangeStart; }
    public void setPortRangeStart(int portRangeStart) { this.portRangeStart = portRangeStart; }

    public int getPortRangeEnd() { return portRangeEnd; }
    public void setPortRangeEnd(int portRangeEnd) { this.portRangeEnd = portRangeEnd; }

    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String internalToken) { this.internalToken = internalToken; }

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }

    public long getJwtExpirationMs() { return jwtExpirationMs; }
    public void setJwtExpirationMs(long jwtExpirationMs) { this.jwtExpirationMs = jwtExpirationMs; }

    public String getContainerImage() { return containerImage; }
    public void setContainerImage(String containerImage) { this.containerImage = containerImage; }

    public String getDockerHost() { return dockerHost; }
    public void setDockerHost(String dockerHost) { this.dockerHost = dockerHost; }

    public String getDashscopeApiKey() { return dashscopeApiKey; }
    public void setDashscopeApiKey(String dashscopeApiKey) { this.dashscopeApiKey = dashscopeApiKey; }

    public String getPlatformNetwork() { return platformNetwork; }
    public void setPlatformNetwork(String platformNetwork) { this.platformNetwork = platformNetwork; }
}
