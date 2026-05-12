package com.signalpulse.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Single source of truth for building the SP_AUTH cookie. Both the login
 * endpoint and the sliding-session refresh filter use this so the cookie
 * attributes (Secure, SameSite, Path) stay in lockstep.
 */
@Component
public class AuthCookieFactory {

    /** Cookie name used to carry the JWT. */
    public static final String AUTH_COOKIE = "SP_AUTH";

    public ResponseCookie build(String value, Duration maxAge, HttpServletRequest request) {
        boolean secure = request.isSecure()
                || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        if (Boolean.getBoolean("signalpulse.auth.forceInsecureCookie")) {
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
}
