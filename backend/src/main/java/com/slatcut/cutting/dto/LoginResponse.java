package com.slatcut.cutting.dto;

public record LoginResponse(String token, String username, String role, long expiresInMs) {
}
