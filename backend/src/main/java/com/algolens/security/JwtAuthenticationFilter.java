package com.algolens.security;

import com.algolens.entity.Role;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code Authorization: Bearer <jwt>} and populates the security context.
 *
 * <p>The principal is rebuilt from the token's own claims rather than by loading the user from
 * the database, which keeps authenticated requests to a single query budget. The trade-off is
 * that a role change or deletion only takes effect when the token expires -- acceptable at a
 * 24 hour TTL, and the reason token lifetime is configurable.
 *
 * <p>An absent or invalid token is not an error here: the filter simply leaves the context
 * anonymous and lets the authorization rules decide. That is what makes anonymous execution
 * possible on the same endpoint that serves signed-in users.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            bearerToken(request)
                    .flatMap(jwtService::verify)
                    .ifPresent(claims -> authenticate(claims, request));
        }
        chain.doFilter(request, response);
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        Long userId = JwtService.userIdOf(claims);
        if (userId == null) {
            return;
        }
        AuthUser principal = new AuthUser(userId, claims.getSubject(), JwtService.nameOf(claims),
                null, Role.USER);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null,
                        principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
