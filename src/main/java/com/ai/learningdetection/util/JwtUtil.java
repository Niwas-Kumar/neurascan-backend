package com.ai.learningdetection.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-threshold}")
    private long jwtRefreshThreshold;

    // -------------------------------------------------------
    // Token Generation
    // -------------------------------------------------------

    public String generateToken(String email, String role, String userId) {
        return generateToken(email, role, userId, null, null);
    }

    public String generateToken(String email, String role, String userId, String name) {
        return generateToken(email, role, userId, name, null);
    }

    public String generateToken(String email, String role, String userId, String name, String picture) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("userId", userId);
        if (name != null) {
            claims.put("name", name);
        }
        if (picture != null) {
            claims.put("picture", picture);
        }
        return buildToken(claims, email, jwtExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // -------------------------------------------------------
    // Token Validation
    // -------------------------------------------------------

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Check if token should be refreshed (less than threshold time remaining).
     * Returns true if token is still valid but should be refreshed.
     */
    public boolean shouldRefreshToken(String token) {
        try {
            Date expiration = extractExpiration(token);
            long timeRemaining = expiration.getTime() - System.currentTimeMillis();
            return timeRemaining > 0 && timeRemaining < jwtRefreshThreshold;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Refresh token by issuing a new one with same claims but new expiration.
     */
    public String refreshToken(String oldToken) {
        try {
            Claims claims = extractAllClaims(oldToken);
            String email = claims.getSubject();
            String role = claims.get("role", String.class);
            String userId = claims.get("userId", String.class);
            String name = claims.get("name", String.class);
            String picture = claims.get("picture", String.class);

            return generateToken(email, role, userId, name, picture);
        } catch (Exception e) {
            log.error("Failed to refresh token: {}", e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------
    // Claims Extraction
    // -------------------------------------------------------

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public String extractUserId(String token) {
        Object userId = extractClaim(token, claims -> claims.get("userId"));
        return userId != null ? userId.toString() : null;
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(jwtSecret.getBytes())
        );
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }
}

