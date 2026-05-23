package com.HendrikHoemberg.StudyHelper.config;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserLocaleResolverTests {

    private UserRepository userRepository;
    private UserLocaleResolver resolver;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        resolver = new UserLocaleResolver(userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private HttpServletRequest requestWithAcceptLanguage(String header) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (header != null) req.addHeader("Accept-Language", header);
        return req;
    }

    private void authenticateAs(String username) {
        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername(username).password("x")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER"))).build();
        var auth = new UsernamePasswordAuthenticationToken(userDetails, "x", userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void authenticatedUserWithGermanLanguagePref_returnsGerman() {
        User user = new User();
        user.setUsername("alice");
        user.setLanguage("de");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        authenticateAs("alice");

        assertThat(resolver.resolveLocale(requestWithAcceptLanguage("en-US")))
                .isEqualTo(Locale.GERMAN);
    }

    @Test
    void authenticatedUserWithEnglishLanguagePref_returnsEnglish() {
        User user = new User();
        user.setUsername("alice");
        user.setLanguage("en");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        authenticateAs("alice");

        assertThat(resolver.resolveLocale(requestWithAcceptLanguage("de-DE")))
                .isEqualTo(Locale.ENGLISH);
    }

    @Test
    void authenticatedUserWithNullLanguage_fallsBackToAcceptLanguageGerman() {
        User user = new User();
        user.setUsername("alice");
        user.setLanguage(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        authenticateAs("alice");

        assertThat(resolver.resolveLocale(requestWithAcceptLanguage("de-DE,de;q=0.9,en;q=0.5")))
                .isEqualTo(Locale.GERMAN);
    }

    @Test
    void authenticatedUserWithNullLanguage_fallsBackToAcceptLanguageEnglish() {
        User user = new User();
        user.setUsername("alice");
        user.setLanguage(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        authenticateAs("alice");

        assertThat(resolver.resolveLocale(requestWithAcceptLanguage("en-US")))
                .isEqualTo(Locale.ENGLISH);
    }

    @Test
    void anonymousUserWithGermanAcceptLanguage_returnsGerman() {
        assertThat(resolver.resolveLocale(requestWithAcceptLanguage("de-DE")))
                .isEqualTo(Locale.GERMAN);
    }

    @Test
    void anonymousUserWithFrenchAcceptLanguage_clampsToEnglish() {
        assertThat(resolver.resolveLocale(requestWithAcceptLanguage("fr-FR")))
                .isEqualTo(Locale.ENGLISH);
    }

    @Test
    void anonymousUserWithNoAcceptLanguage_returnsEnglish() {
        assertThat(resolver.resolveLocale(requestWithAcceptLanguage(null)))
                .isEqualTo(Locale.ENGLISH);
    }

    @Test
    void resolveCachesResultOnRequestAttribute() {
        User user = new User();
        user.setUsername("alice");
        user.setLanguage("de");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        authenticateAs("alice");

        HttpServletRequest req = requestWithAcceptLanguage("en-US");
        resolver.resolveLocale(req);
        resolver.resolveLocale(req);

        verify(userRepository, times(1)).findByUsername("alice");
    }
}
