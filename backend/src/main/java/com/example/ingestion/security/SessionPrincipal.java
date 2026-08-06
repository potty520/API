package com.example.ingestion.security;

import java.util.List;

public record SessionPrincipal(Long id, String username, String displayName, String role, List<String> permissions) {}
