package com.example.islab.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service

public class JwtService {
	private final SecretKey key;
	private final long expirationMs;

	public JwtService(@Value("${app.jwt.secret}") String secret,
	                  @Value("${app.jwt.expiration-ms}") long expirationMs) {
	    this.key = Keys.hmacShaKeyFor(secret.getBytes());
	    this.expirationMs = expirationMs;
	}

	public String generateToken(String username) {
	    Date now = new Date();
	    return Jwts.builder()
	            .subject(username)
	            .issuedAt(now)
	            .expiration(new Date(now.getTime() + expirationMs))
	            .signWith(key)
	            .compact();
	}

	public String extractUsername(String token) {
	    return parse(token).getSubject();
	}

	public boolean isValid(String token) {
	    try {
	        parse(token);
	        return true;
	    } catch (JwtException | IllegalArgumentException e) {
	        return false;
	    }
	}

	public long getExpirationMs() { 
		return expirationMs; 
	}

	private Claims parse(String token) {
	    return Jwts.parser()
	            .verifyWith(key)
	            .build()
	            .parseSignedClaims(token)
	            .getPayload();
	}
}
