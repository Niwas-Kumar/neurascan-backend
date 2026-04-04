package com.ai.learningdetection.security;

import com.ai.learningdetection.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AppUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            if (!jwtUtil.validateToken(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }

            final String userEmail = jwtUtil.extractUsername(jwt);

            if (StringUtils.hasText(userEmail)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                if (jwtUtil.isTokenValid(jwt, userDetails) && claimsMatchPrincipal(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // Auto-refresh token if it's close to expiration
                    if (jwtUtil.shouldRefreshToken(jwt)) {
                        String refreshedToken = jwtUtil.refreshToken(jwt);
                        if (refreshedToken != null) {
                            response.setHeader("X-New-Token", refreshedToken);
                            log.debug("Token auto-refreshed for user: {}", userEmail);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication for request {}: {} - {}", 
                request.getRequestURI(), e.getClass().getSimpleName(), e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Authentication error details:", e);
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean claimsMatchPrincipal(String jwt, UserDetails userDetails) {
        try {
            String tokenRole = jwtUtil.extractRole(jwt);
            boolean roleMatches = userDetails.getAuthorities().stream()
                    .anyMatch(a -> Objects.equals(a.getAuthority(), tokenRole));

            if (!roleMatches) {
                return false;
            }

            if (userDetails instanceof IdentifiablePrincipal principal) {
                String tokenUserId = jwtUtil.extractUserId(jwt);
                return Objects.equals(principal.getId(), tokenUserId);
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
