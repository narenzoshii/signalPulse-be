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

@Component
@Slf4j
public class RequestResponseLoggingFilter implements Filter {

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
        String requestBody = new String(request.getContentAsByteArray(), StandardCharsets.UTF_8);
        String responseBody = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);

        log.info("API CALL: [{} {}] - Status: {} - Payload: {} - Response: {}", 
                request.getMethod(), 
                request.getRequestURI(), 
                response.getStatus(),
                requestBody.isEmpty() ? "ABSENT" : requestBody,
                responseBody.isEmpty() ? "ABSENT" : responseBody
        );
    }
}
