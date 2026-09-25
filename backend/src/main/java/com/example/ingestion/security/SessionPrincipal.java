package com.example.ingestion.security;

import java.util.List;

public record SessionPrincipal(Long id, String username, String displayName, String role, List<String> permissions,
                               boolean mustChangePassword) {
    /** 用于审计等场景构造不携带会话状态的占位主体。 */
    public static SessionPrincipal anonymous(String username) {
        return new SessionPrincipal(null, username, "", "", List.of(), false);
    }
}
