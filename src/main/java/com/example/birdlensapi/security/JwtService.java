package com.example.birdlensapi.security;

import com.example.birdlensapi.domain.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiry-minutes}")
    private long expiryMinutes;

    @Value("${app.jwt.refresh-expiry-days}")
    private long refreshExpiryDays;

    // ── Token generation ──────────────────────────────────────────────────────

    // Access token includes userId and roles claims (AC #4)
    public String generateAccessToken(UserDetails userDetails) {
        long expiryMillis = expiryMinutes * 60 * 1000L;
        long now = System.currentTimeMillis();

        var builder = Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMillis));

        // Attach userId and roles when the principal is our User entity
        if (userDetails instanceof User user) {
            builder.claim("userId", user.getId().toString());
            List<String> roles = user.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();
            builder.claim("roles", roles);
        }

        return builder.signWith(getSigningKey()).compact();
    }

    // Refresh token is intentionally minimal — subject only, longer expiry
    public String generateRefreshToken(UserDetails userDetails) {
        return buildMinimalToken(userDetails, refreshExpiryDays * 24 * 60 * 60 * 1000L);
    }

    private String buildMinimalToken(UserDetails userDetails, long expiryMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMillis))
                .signWith(getSigningKey())
                .compact();
    }

    // ── Token validation ──────────────────────────────────────────────────────

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ── Claims extraction ─────────────────────────────────────────────────────

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", String.class));
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractClaim(token, claims -> claims.get("roles", List.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ── Key ───────────────────────────────────────────────────────────────────

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}