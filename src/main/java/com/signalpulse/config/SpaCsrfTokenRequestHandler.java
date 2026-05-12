package com.signalpulse.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * Canonical Spring 6 CSRF handler for SPAs that read the XSRF-TOKEN cookie
 * and echo it in the X-XSRF-TOKEN header.
 *
 * Spring's default {@link XorCsrfTokenRequestAttributeHandler} XOR-masks the
 * token for traditional form templates. A SPA, however, reads the *cookie*
 * value (which is the raw token) and sends it back unchanged — so when the
 * server XOR-decodes it, the comparison fails and every POST gets 403.
 *
 * This handler keeps XOR masking for parameter-based submissions (forms)
 * but uses plain comparison for header-based submissions (SPA).
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        // Always publish the XOR'd token to request attributes (server-side
        // templates that include the token in HTML stay BREACH-safe).
        xor.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        // If the client sent it via header → it's the raw cookie value (SPA).
        // Otherwise it came from a form parameter → it's XOR-encoded.
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return StringUtils.hasText(headerValue)
                ? plain.resolveCsrfTokenValue(request, csrfToken)
                : xor.resolveCsrfTokenValue(request, csrfToken);
    }
}
