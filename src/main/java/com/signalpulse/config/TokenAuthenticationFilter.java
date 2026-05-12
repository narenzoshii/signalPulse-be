package com.signalpulse.config;

import com.signalpulse.security.AuthCookieFactory;
import com.signalpulse.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Date;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final CustomUserDetailsService userDetailsService;
    private final com.signalpulse.security.JwtService jwtService;
    private final AuthCookieFactory cookieFactory;

    @Override
    protected void doFilterInternal(
            @jakarta.annotation.Nonnull HttpServletRequest request,
            @jakarta.annotation.Nonnull HttpServletResponse response,
            @jakarta.annotation.Nonnull FilterChain filterChain
    ) throws ServletException, IOException {

        String jwt = extractJwt(request);
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String username;
        try {
            username = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                // Sliding session: if the token is past the halfway mark of its
                // lifetime, mint a fresh one so an active user never bumps into
                // the 24-h hard expiry mid-session.
                maybeRefreshCookie(request, response, userDetails, jwt);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void maybeRefreshCookie(HttpServletRequest request, HttpServletResponse response, UserDetails userDetails, String jwt) {
        try {
            Date issuedAt = jwtService.extractIssuedAt(jwt);
            if (issuedAt == null) return;
            long ageMs = System.currentTimeMillis() - issuedAt.getTime();
            long lifetimeMs = jwtService.getExpirationMs();
            if (ageMs >= lifetimeMs / 2) {
                String fresh = jwtService.generateToken(userDetails);
                ResponseCookie cookie = cookieFactory.build(fresh, Duration.ofMillis(lifetimeMs), request);
                response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            }
        } catch (Exception e) {
            // Refresh is best-effort; never break the request because of it.
            log.debug("Cookie refresh skipped: {}", e.getMessage());
        }
    }

    private String extractJwt(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (AuthCookieFactory.AUTH_COOKIE.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
