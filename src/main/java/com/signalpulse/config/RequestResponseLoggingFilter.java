package com.signalpulse.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@Slf4j
public class RequestResponseLoggingFilter implements Filter {

    private static final Set<String> SENSITIVE_PATH_PREFIXES = Set.of(
            "/api/v1/auth",
            "/api/v1/config",
            "/api/v1/users"
    );

    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(?i)\"(password|token|secret|aes[_-]?key|gmail[_-]?password)\"\\s*:\\s*\"([^\"]*)\"");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper((HttpServletRequest) request, 1024 * 1024);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper((HttpServletResponse) response);

        try {
            chain.doFilter(requestWrapper, responseWrapper);
        } finally {
            logRequestResponse(requestWrapper, responseWrapper);
            responseWrapper.copyBodyToResponse();
        }
    }

    private void logRequestResponse(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response) {
        String uri = request.getRequestURI();
        boolean sensitive = isSensitivePath(uri);

        String requestBody = sensitive ? "[REDACTED]" : sanitize(new String(request.getContentAsByteArray(), StandardCharsets.UTF_8));
        String responseBody = sensitive ? "[REDACTED]" : sanitize(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));

        if (requestBody.length() > 1000) {
            requestBody = requestBody.substring(0, 1000) + "... [truncated]";
        }
        if (responseBody.length() > 1000) {
            responseBody = responseBody.substring(0, 1000) + "... [truncated]";
        }

        log.info("API CALL: [{} {}] - Status: {} - Payload: {} - Response: {}",
                request.getMethod(),
                uri,
                response.getStatus(),
                requestBody.isEmpty() ? "ABSENT" : requestBody,
                responseBody.isEmpty() ? "ABSENT" : responseBody
        );
    }

    private boolean isSensitivePath(String uri) {
        if (uri == null) return false;
        for (String prefix : SENSITIVE_PATH_PREFIXES) {
            if (uri.startsWith(prefix)) return true;
        }
        return false;
    }

    private String sanitize(String body) {
        if (body == null || body.isEmpty()) return body;
        return SENSITIVE_FIELD_PATTERN.matcher(body).replaceAll("\"$1\":\"[REDACTED]\"");
    }
}
