package com.openclaw.manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 64)
    private String username;

    @NotBlank
    @Size(min = 6, max = 128)
    private String password;

    // Optional: platform uses a shared API key, per-user key is kept for future use
    private String dashscopeKey = "";

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDashscopeKey() { return dashscopeKey; }
    public void setDashscopeKey(String dashscopeKey) { this.dashscopeKey = dashscopeKey; }
}
