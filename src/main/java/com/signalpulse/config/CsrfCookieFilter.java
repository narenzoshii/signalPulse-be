package com.signalpulse.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring's {@code CookieCsrfTokenRepository} only writes the XSRF-TOKEN
 * cookie on requests that actively read the {@link CsrfToken}. A pure REST
 * API never touches it, so the SPA never gets a token to echo back in
 * X-XSRF-TOKEN — and every mutating call lands as 403.
 *
 * This filter forces token resolution after the {@code CsrfFilter} has put
 * the deferred token on the request, which in turn causes the repository to
 * write the cookie to the response.
 */
@Component
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            // Materialise the lazy supplier so the cookie is actually written.
            token.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
