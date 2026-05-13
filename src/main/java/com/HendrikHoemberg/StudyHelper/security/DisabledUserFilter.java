package com.HendrikHoemberg.StudyHelper.security;

import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@ConditionalOnBean(UserRepository.class)
public class DisabledUserFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public DisabledUserFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken)) {
            String username = authentication.getName();
            if (username != null && !username.isBlank()) {
                boolean invalidSessionUser = userRepository.findByUsername(username)
                    .map(user -> !user.isEnabled())
                    .orElse(true);
                if (invalidSessionUser) {
                    SecurityContextHolder.clearContext();
                    if (request.getSession(false) != null) {
                        request.getSession(false).invalidate();
                    }
                    response.sendRedirect("/login?disabled");
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
