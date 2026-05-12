package com.signalpulse.controller;

import com.signalpulse.dto.LoginRequest;
import com.signalpulse.security.AuthCookieFactory;
import com.signalpulse.service.ConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    /** Cookie name used to carry the JWT. HttpOnly + SameSite=Lax. */
    public static final String AUTH_COOKIE = AuthCookieFactory.AUTH_COOKIE;

    private static final long DEFAULT_IDLE_TIMEOUT_MINS = 30;

    private final AuthenticationManager authenticationManager;
    private final com.signalpulse.security.JwtService jwtService;
    private final com.signalpulse.service.CustomUserDetailsService userDetailsService;
    private final AuthCookieFactory cookieFactory;
    private final ConfigService configService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(body.getUsername(), body.getPassword())
            );
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(body.getUsername());
        String jwt = jwtService.generateToken(userDetails);

        // Force CSRF token materialisation so XSRF-TOKEN cookie ships with the
        // login response. CsrfCookieFilter normally handles this, but login is
        // in ignoringRequestMatchers and behavior there varies across versions;
        // without this, the first POST after login lands as 403 because the FE
        // has no token to echo back in X-XSRF-TOKEN.
        materializeCsrfToken(request);

        ResponseCookie cookie = cookieFactory.build(jwt, Duration.ofMillis(jwtService.getExpirationMs()), request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(buildUserPayload(userDetails));
    }

    private void materializeCsrfToken(HttpServletRequest request) {
        Object csrf = request.getAttribute(CsrfToken.class.getName());
        if (csrf instanceof CsrfToken token) {
            try { token.getToken(); } catch (Exception ignored) { /* best-effort */ }
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        ResponseCookie cookie = cookieFactory.build("", Duration.ZERO, request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        Object principal = authentication.getPrincipal();
        UserDetails userDetails = (principal instanceof UserDetails ud)
                ? ud
                : userDetailsService.loadUserByUsername(authentication.getName());
        return ResponseEntity.ok(buildUserPayload(userDetails));
    }

    private Map<String, Object> buildUserPayload(UserDetails userDetails) {
        Set<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        List<Map<String, String>> roles = authorities.stream()
                .filter(a -> a.startsWith("ROLE_"))
                .map(role -> Map.of("name", role.replace("ROLE_", "")))
                .collect(Collectors.toList());

        return Map.of(
                "user", Map.of(
                        "username", userDetails.getUsername(),
                        "roles", roles
                ),
                "authorities", authorities,
                "session", Map.of(
                        "idleTimeoutMins", resolveIdleTimeoutMins(),
                        "absoluteExpiryMs", jwtService.getExpirationMs()
                )
        );
    }

    private long resolveIdleTimeoutMins() {
        try {
            return Long.parseLong(configService.getConfig("SESSION_TIMEOUT_MINS").orElse(String.valueOf(DEFAULT_IDLE_TIMEOUT_MINS)));
        } catch (NumberFormatException e) {
            return DEFAULT_IDLE_TIMEOUT_MINS;
        }
    }
}
