package com.HendrikHoemberg.StudyHelper.config;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class UserLocaleResolver extends AcceptHeaderLocaleResolver {

    private static final String CACHE_ATTR = "studyhelper.resolvedLocale";

    private final UserRepository userRepository;

    public UserLocaleResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
        setDefaultLocale(Locale.ENGLISH);
        setSupportedLocales(List.of(Locale.ENGLISH, Locale.GERMAN));
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        Object cached = request.getAttribute(CACHE_ATTR);
        if (cached instanceof Locale c) return c;

        Locale resolved = resolveFresh(request);
        request.setAttribute(CACHE_ATTR, resolved);
        return resolved;
    }

    private Locale resolveFresh(HttpServletRequest request) {
        String username = currentUsername();
        if (username != null) {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isPresent() && userOpt.get().getLanguage() != null) {
                return "de".equals(userOpt.get().getLanguage()) ? Locale.GERMAN : Locale.ENGLISH;
            }
        }
        Locale browser = super.resolveLocale(request);
        return browser != null && "de".equals(browser.getLanguage()) ? Locale.GERMAN : Locale.ENGLISH;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails ud) return ud.getUsername();
        return null;
    }
}
