package com.example.islab.dto;

public record LoginResponse(String token, String type, long expiresInMs) {}