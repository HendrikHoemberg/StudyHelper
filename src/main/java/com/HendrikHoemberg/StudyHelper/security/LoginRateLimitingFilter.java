package com.HendrikHoemberg.StudyHelper.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class LoginRateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitingFilter.class);

    private final LoginAttemptService loginAttemptService;

    public LoginRateLimitingFilter(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isLoginRequest(request)) {
            String ip = LoginAttemptService.extractClientIP(request);
            if (loginAttemptService.isBlocked(ip)) {
                log.warn("Login request blocked for IP: {} - too many failed attempts", ip);
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Too many login attempts. Please try again later.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        return "/login".equals(request.getRequestURI())
            && "POST".equalsIgnoreCase(request.getMethod());
    }
}
