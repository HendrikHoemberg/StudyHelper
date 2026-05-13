package com.HendrikHoemberg.StudyHelper.security;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisabledUserFilterTests {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_DisabledAuthenticatedUser_RedirectsAndClearsContext() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        DisabledUserFilter filter = new DisabledUserFilter(userRepository);
        FilterChain filterChain = mock(FilterChain.class);

        User disabledUser = new User();
        disabledUser.setUsername("alice");
        disabledUser.setEnabled(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(disabledUser));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "alice",
                "pw",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
            )
        );

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?disabled");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_AuthenticatedUserMissingFromRepository_RedirectsAndClearsContext() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        DisabledUserFilter filter = new DisabledUserFilter(userRepository);
        FilterChain filterChain = mock(FilterChain.class);

        when(userRepository.findByUsername("missing-user")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "missing-user",
                "pw",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
            )
        );

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?disabled");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, never()).doFilter(request, response);
    }
}
