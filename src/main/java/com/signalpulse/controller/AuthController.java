package com.signalpulse.controller;

import com.signalpulse.dto.LoginRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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
    public static final String AUTH_COOKIE = "SP_AUTH";

    private final AuthenticationManager authenticationManager;
    private final com.signalpulse.security.JwtService jwtService;
    private final com.signalpulse.service.CustomUserDetailsService userDetailsService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(body.getUsername(), body.getPassword())
            );
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(body.getUsername());
        String jwt = jwtService.generateToken(userDetails);

        ResponseCookie cookie = buildAuthCookie(jwt, Duration.ofDays(1));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(buildUserPayload(userDetails));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = buildAuthCookie("", Duration.ZERO);
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

    private ResponseCookie buildAuthCookie(String value, Duration maxAge) {
        boolean secure = true; // require HTTPS in prod; modern browsers also accept on localhost
        // Allow override via system property for plain-HTTP local dev if absolutely needed
        if (Boolean.getBoolean("signalpulse.auth.insecureCookie")) {
            secure = false;
        }
        return ResponseCookie.from(AUTH_COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
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
                "authorities", authorities
        );
    }
}
