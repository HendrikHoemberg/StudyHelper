# Settings Page, Password Change, and Bilingual (DE/EN) Support — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a `/settings` page (password change + language switch) and convert the entire StudyHelper UI to be bilingual (German / English) via Spring `MessageSource` bundles, with a `window.i18n` bridge for client-side strings.

**Architecture:** Add a nullable `language` column on `User`; new `SettingsController` with two POST endpoints; new `UserLocaleResolver` that prefers `user.language` and falls back to `Accept-Language` clamped to `{de, en}`; new `I18nConfig` registering `MessageSource` + resolver; `GlobalControllerAdvice` exposes `currentLocale` and a `js.*`-prefixed `i18nJs` map injected into every page via a new `fragments/i18n-bridge.html`; replace username display in topnav + mobile bottom-sheet with a `/settings` link; replace every English literal in templates with `#{key}` and every English literal in JS with `window.i18n.key`; produce `messages.properties` and `messages_de.properties`.

**Tech Stack:** Spring Boot 3 / Spring MVC, Thymeleaf, JPA + Hibernate (ddl-auto=update, MySQL), Spring Security, JUnit 5 + Mockito + AssertJ + MockMvc.

---

## Spec Reference

Design spec: `docs/superpowers/specs/2026-05-23-settings-page-and-i18n-design.md`.

## Phase Overview

1. **Phase 1 — Foundation** (Tasks 1–7): Add `language` column, build `UserLocaleResolver`, `I18nConfig`, empty `messages.properties` bundles. App still renders English; tests prove the locale plumbing works.
2. **Phase 2 — Settings page** (Tasks 8–13): `SettingsController`, `UserService.changePassword` + `setLanguage`, `settings.html` template, navbar/drawer swap. Page is functional with English hard-coded keys-as-fallback.
3. **Phase 3 — i18n bridge** (Tasks 14–16): `i18n-bridge.html` fragment, `GlobalControllerAdvice.i18nJs` builder, wire into `layout.html`.
4. **Phase 4 — Extract English strings** (Tasks 17–26): Walk templates + JS file-group by file-group, replacing literals with translation keys. Each task ships a coherent slice the app stays runnable in English.
5. **Phase 5 — German translations** (Task 27): Produce `messages_de.properties` from the now-complete `messages.properties`.
6. **Phase 6 — Polish** (Tasks 28–30): Locale-aware date formatting; manual verification; final commit.

---

## Phase 1 — Foundation

### Task 1: Add `language` column to `User`

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/entity/User.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/UserServiceTests.java`

- [ ] **Step 1: Write the failing test**

Append to `UserServiceTests.java` inside the class:

```java
@Test
void newUser_HasNullLanguageByDefault() {
    User user = new User();
    assertThat(user.getLanguage()).isNull();
}
```

- [ ] **Step 2: Run test, verify it fails**

```
./mvnw -Dtest=UserServiceTests#newUser_HasNullLanguageByDefault test
```
Expected: compilation failure — `getLanguage()` not defined.

- [ ] **Step 3: Add the field**

Add to `User.java` after the existing `private int dailyAiRequestLimit` field (around line 43):

```java
@Column(length = 8)
private String language;
```

(Field is nullable — no `nullable = false`. The Lombok `@Getter`/`@Setter` annotations on the class generate `getLanguage()`/`setLanguage(String)` automatically.)

- [ ] **Step 4: Run test, verify it passes**

```
./mvnw -Dtest=UserServiceTests#newUser_HasNullLanguageByDefault test
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/entity/User.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/UserServiceTests.java
git commit -m "feat(user): add nullable language column to User entity"
```

---

### Task 2: Create empty `messages.properties` bundle

**Files:**
- Create: `src/main/resources/messages.properties`
- Create: `src/main/resources/messages_de.properties`

- [ ] **Step 1: Create the English bundle**

Write `src/main/resources/messages.properties` with a single placeholder key so the file is non-empty:

```
# UI translations (English) — source of truth
# Add keys in dotted lowercase namespaces, e.g. nav.dashboard, settings.password.title.
# Keys prefixed with `js.` are injected into window.i18n for client-side use.
app.brand=StudyHelper
```

- [ ] **Step 2: Create the German bundle**

Write `src/main/resources/messages_de.properties` (UTF-8) with the same key:

```
app.brand=StudyHelper
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/messages.properties src/main/resources/messages_de.properties
git commit -m "feat(i18n): add empty messages bundles for en and de"
```

---

### Task 3: Add `I18nConfig` with `MessageSource` bean

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/config/I18nConfig.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/config/I18nConfigTests.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/HendrikHoemberg/StudyHelper/config/I18nConfigTests.java`:

```java
package com.HendrikHoemberg.StudyHelper.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class I18nConfigTests {

    @Autowired
    private MessageSource messageSource;

    @Test
    void resolvesEnglishKey() {
        assertThat(messageSource.getMessage("app.brand", null, Locale.ENGLISH))
            .isEqualTo("StudyHelper");
    }

    @Test
    void resolvesGermanKey() {
        assertThat(messageSource.getMessage("app.brand", null, Locale.GERMAN))
            .isEqualTo("StudyHelper");
    }

    @Test
    void missingKeyReturnsKeyAsFallback() {
        assertThat(messageSource.getMessage("nonexistent.key", null, Locale.ENGLISH))
            .isEqualTo("nonexistent.key");
    }
}
```

- [ ] **Step 2: Run tests, verify they fail**

```
./mvnw -Dtest=I18nConfigTests test
```
Expected: FAIL — Spring Boot's auto-configured `MessageSource` doesn't load `classpath:messages` by default in this app, and `useCodeAsDefaultMessage` defaults to false.

- [ ] **Step 3: Create the config**

Write `src/main/java/com/HendrikHoemberg/StudyHelper/config/I18nConfig.java`:

```java
package com.HendrikHoemberg.StudyHelper.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Locale;

@Configuration
public class I18nConfig implements WebMvcConfigurer {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        ms.setDefaultLocale(Locale.ENGLISH);
        ms.setUseCodeAsDefaultMessage(true);
        return ms;
    }
}
```

- [ ] **Step 4: Run tests, verify they pass**

```
./mvnw -Dtest=I18nConfigTests test
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/config/I18nConfig.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/config/I18nConfigTests.java
git commit -m "feat(i18n): add I18nConfig with MessageSource bean"
```

---

### Task 4: Add `UserLocaleResolver`

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/config/UserLocaleResolver.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/config/UserLocaleResolverTests.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/HendrikHoemberg/StudyHelper/config/UserLocaleResolverTests.java`:

```java
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
import org.springframework.security.core.userdetails.User.UserBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.times(1))
            .findByUsername("alice");
    }
}
```

- [ ] **Step 2: Run tests, verify they fail to compile**

```
./mvnw -Dtest=UserLocaleResolverTests test
```
Expected: compilation failure — `UserLocaleResolver` not defined.

- [ ] **Step 3: Create the resolver**

Write `src/main/java/com/HendrikHoemberg/StudyHelper/config/UserLocaleResolver.java`:

```java
package com.HendrikHoemberg.StudyHelper.config;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;
import java.util.Optional;

public class UserLocaleResolver extends AcceptHeaderLocaleResolver {

    private static final String CACHE_ATTR = "studyhelper.resolvedLocale";

    private final UserRepository userRepository;

    public UserLocaleResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
        setDefaultLocale(Locale.ENGLISH);
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
```

- [ ] **Step 4: Run tests, verify they pass**

```
./mvnw -Dtest=UserLocaleResolverTests test
```
Expected: all 8 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/config/UserLocaleResolver.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/config/UserLocaleResolverTests.java
git commit -m "feat(i18n): add UserLocaleResolver with user-pref + Accept-Language fallback"
```

---

### Task 5: Register `UserLocaleResolver` as the app's `LocaleResolver`

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/config/I18nConfig.java`

- [ ] **Step 1: Add the bean**

Modify `I18nConfig.java` — add the import and a new `@Bean` method:

```java
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import org.springframework.web.servlet.LocaleResolver;
```

Add inside the class (after the existing `messageSource()` method):

```java
    @Bean
    public LocaleResolver localeResolver(UserRepository userRepository) {
        return new UserLocaleResolver(userRepository);
    }
```

- [ ] **Step 2: Verify the app boots**

```
./mvnw -Dtest=StudyHelperApplicationTests test
```
Expected: PASS. Spring context loads with the new `LocaleResolver` bean.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/config/I18nConfig.java
git commit -m "feat(i18n): register UserLocaleResolver as the app LocaleResolver"
```

---

### Task 6: Expose `currentLocale` via `GlobalControllerAdvice`

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/GlobalControllerAdvice.java`

- [ ] **Step 1: Add the model attribute**

Add to `GlobalControllerAdvice.java`:

Import:
```java
import org.springframework.context.i18n.LocaleContextHolder;
```

Add method inside the class (before the closing brace):

```java
    @ModelAttribute("currentLocale")
    public String addCurrentLocale() {
        return LocaleContextHolder.getLocale().getLanguage();
    }
```

- [ ] **Step 2: Verify by running existing tests**

```
./mvnw test
```
Expected: all existing tests still PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/GlobalControllerAdvice.java
git commit -m "feat(i18n): expose currentLocale to all views via GlobalControllerAdvice"
```

---

### Task 7: Apply current locale to `<html lang>` on `layout.html` head

**Files:**
- Modify: `src/main/resources/templates/fragments/layout.html`
- Modify (all pages that use `layout.html :: head`): they all already pull `<head>` from the fragment — only the `<html>` element on each page hard-codes `lang="en"`. The `<html lang>` attribute lives on the page templates, not the fragment. We'll add a wrapper attribute via a JS line in the head script. Simpler: set `document.documentElement.lang` from JS at the top of the existing head script.

- [ ] **Step 1: Modify the head script in `layout.html`**

In `fragments/layout.html`, replace the existing `<script>` block in the `head` fragment (lines 32–37):

```html
    <script th:inline="javascript">
        (function() {
            var t = localStorage.getItem('theme') || 'light';
            document.documentElement.dataset.theme = t;
            var lang = /*[[${currentLocale}]]*/ 'en';
            if (lang) document.documentElement.lang = lang;
        })();
    </script>
```

- [ ] **Step 2: Verify by running app tests**

```
./mvnw -Dtest=UiResourceRegressionTests test
```
Expected: PASS (this test renders templates, so it confirms Thymeleaf processes the new `th:inline` block without error).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/fragments/layout.html
git commit -m "feat(i18n): sync html lang attribute with current locale"
```

---

## Phase 2 — Settings page

### Task 8: Add `UserService.changePassword` with tests

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/UserService.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/UserServiceTests.java`

- [ ] **Step 1: Write the failing tests**

Add to `UserServiceTests.java`:

```java
@Test
void changePassword_HappyPath_EncodesAndSaves() {
    User existing = new User();
    existing.setUsername("alice");
    existing.setPassword("encoded-old");
    when(passwordEncoder.matches("oldpw1234", "encoded-old")).thenReturn(true);
    when(passwordEncoder.encode("newpw5678")).thenReturn("encoded-new");

    userService.changePassword(existing, "oldpw1234", "newpw5678", "newpw5678");

    assertThat(existing.getPassword()).isEqualTo("encoded-new");
    verify(userRepository, times(1)).save(existing);
}

@Test
void changePassword_WrongCurrent_Throws() {
    User existing = new User();
    existing.setPassword("encoded-old");
    when(passwordEncoder.matches("wrong", "encoded-old")).thenReturn(false);

    assertThatThrownBy(() ->
        userService.changePassword(existing, "wrong", "newpw5678", "newpw5678"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("errors.password.wrong-current");

    verify(userRepository, never()).save(any());
}

@Test
void changePassword_Mismatch_Throws() {
    User existing = new User();
    existing.setPassword("encoded-old");
    when(passwordEncoder.matches("oldpw1234", "encoded-old")).thenReturn(true);

    assertThatThrownBy(() ->
        userService.changePassword(existing, "oldpw1234", "newpw5678", "different"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("errors.password.mismatch");

    verify(userRepository, never()).save(any());
}

@Test
void changePassword_TooShort_Throws() {
    User existing = new User();
    existing.setPassword("encoded-old");
    when(passwordEncoder.matches("oldpw1234", "encoded-old")).thenReturn(true);

    assertThatThrownBy(() ->
        userService.changePassword(existing, "oldpw1234", "short", "short"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("errors.password.too-short");

    verify(userRepository, never()).save(any());
}
```

- [ ] **Step 2: Run tests, verify they fail**

```
./mvnw -Dtest=UserServiceTests test
```
Expected: 4 new tests FAIL — `changePassword` not defined.

- [ ] **Step 3: Implement `changePassword`**

Add to `UserService.java`:

```java
    @Transactional
    public void changePassword(User user, String currentRaw, String nextRaw, String confirmRaw) {
        if (!passwordEncoder.matches(currentRaw == null ? "" : currentRaw, user.getPassword())) {
            throw new IllegalArgumentException("errors.password.wrong-current");
        }
        if (nextRaw == null || !nextRaw.equals(confirmRaw)) {
            throw new IllegalArgumentException("errors.password.mismatch");
        }
        if (nextRaw.isBlank() || nextRaw.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("errors.password.too-short");
        }
        user.setPassword(passwordEncoder.encode(nextRaw));
        userRepository.save(user);
    }
```

(The thrown messages are translation keys — the controller will look them up against `MessageSource`.)

- [ ] **Step 4: Run tests, verify they pass**

```
./mvnw -Dtest=UserServiceTests test
```
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/UserService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/UserServiceTests.java
git commit -m "feat(user): add changePassword with current-pw + match + length validation"
```

---

### Task 9: Add `UserService.setLanguage` with tests

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/UserService.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/UserServiceTests.java`

- [ ] **Step 1: Write the failing tests**

Add to `UserServiceTests.java`:

```java
@Test
void setLanguage_DE_PersistsDe() {
    User user = new User();
    user.setUsername("alice");

    userService.setLanguage(user, "de");

    assertThat(user.getLanguage()).isEqualTo("de");
    verify(userRepository, times(1)).save(user);
}

@Test
void setLanguage_EN_PersistsEn() {
    User user = new User();
    userService.setLanguage(user, "en");

    assertThat(user.getLanguage()).isEqualTo("en");
    verify(userRepository, times(1)).save(user);
}

@Test
void setLanguage_Invalid_CoercesToEn() {
    User user = new User();
    userService.setLanguage(user, "fr");

    assertThat(user.getLanguage()).isEqualTo("en");
    verify(userRepository, times(1)).save(user);
}

@Test
void setLanguage_Null_CoercesToEn() {
    User user = new User();
    userService.setLanguage(user, null);

    assertThat(user.getLanguage()).isEqualTo("en");
    verify(userRepository, times(1)).save(user);
}
```

- [ ] **Step 2: Run tests, verify they fail**

```
./mvnw -Dtest=UserServiceTests test
```
Expected: 4 new tests FAIL.

- [ ] **Step 3: Implement `setLanguage`**

Add to `UserService.java`:

```java
    @Transactional
    public void setLanguage(User user, String lang) {
        String normalized = "de".equals(lang) ? "de" : "en";
        user.setLanguage(normalized);
        userRepository.save(user);
    }
```

- [ ] **Step 4: Run tests, verify they pass**

```
./mvnw -Dtest=UserServiceTests test
```
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/UserService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/UserServiceTests.java
git commit -m "feat(user): add setLanguage with de/en coercion"
```

---

### Task 10: Add settings keys to `messages.properties`

**Files:**
- Modify: `src/main/resources/messages.properties`

- [ ] **Step 1: Append settings + error keys**

Append to `messages.properties`:

```
# Navigation
nav.settings=Settings

# Settings page
settings.title=Settings
settings.subtitle=Signed in as {0}
settings.password.title=Change password
settings.password.current=Current password
settings.password.new=New password
settings.password.confirm=Confirm new password
settings.password.submit=Update password
settings.password.updated=Password updated.
settings.language.title=Language
settings.language.de=Deutsch
settings.language.en=English
settings.language.submit=Save language
settings.language.updated=Language updated.

# Password errors
errors.password.wrong-current=Current password is incorrect.
errors.password.mismatch=The new passwords do not match.
errors.password.too-short=Password must be at least 8 characters long.
```

(Do NOT update `messages_de.properties` yet — German translations come in Phase 5.)

- [ ] **Step 2: Verify**

```
./mvnw -Dtest=I18nConfigTests test
```
Expected: PASS (existing tests still green; no new tests added here — this is a content-only change).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/messages.properties
git commit -m "feat(i18n): add settings page and password error message keys"
```

---

### Task 11: Add `SettingsController`

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/SettingsController.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/controller/SettingsControllerTests.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/HendrikHoemberg/StudyHelper/controller/SettingsControllerTests.java`:

```java
package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.entity.UserRole;
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettingsControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void seedUser() {
        userRepository.findByUsername("settings-tester").ifPresent(userRepository::delete);
        user = new User();
        user.setUsername("settings-tester");
        user.setPassword(passwordEncoder.encode("currentpw1"));
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        user = userRepository.save(user);
    }

    @Test
    void getSettings_RendersView() throws Exception {
        mockMvc.perform(get("/settings").with(user("settings-tester")))
            .andExpect(status().isOk())
            .andExpect(view().name("settings"));
    }

    @Test
    void postPassword_WrongCurrent_RerendersWithError() throws Exception {
        mockMvc.perform(post("/settings/password")
                .with(user("settings-tester")).with(csrf())
                .param("currentPassword", "wrong")
                .param("newPassword", "newpw5678")
                .param("confirmPassword", "newpw5678"))
            .andExpect(status().isOk())
            .andExpect(view().name("settings"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Current password is incorrect")));
    }

    @Test
    void postPassword_HappyPath_RedirectsAndUpdates() throws Exception {
        mockMvc.perform(post("/settings/password")
                .with(user("settings-tester")).with(csrf())
                .param("currentPassword", "currentpw1")
                .param("newPassword", "newpw5678")
                .param("confirmPassword", "newpw5678"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/settings"));

        User reloaded = userRepository.findByUsername("settings-tester").orElseThrow();
        assertThat(passwordEncoder.matches("newpw5678", reloaded.getPassword())).isTrue();
    }

    @Test
    void postLanguage_PersistsAndRedirects() throws Exception {
        mockMvc.perform(post("/settings/language")
                .with(user("settings-tester")).with(csrf())
                .param("language", "de"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/settings"));

        User reloaded = userRepository.findByUsername("settings-tester").orElseThrow();
        assertThat(reloaded.getLanguage()).isEqualTo("de");
    }
}
```

- [ ] **Step 2: Run tests, verify they fail**

```
./mvnw -Dtest=SettingsControllerTests test
```
Expected: FAIL — no `/settings` route, no `settings` view.

- [ ] **Step 3: Create the controller**

Write `src/main/java/com/HendrikHoemberg/StudyHelper/controller/SettingsController.java`:

```java
package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class SettingsController {

    private final UserService userService;
    private final MessageSource messageSource;

    public SettingsController(UserService userService, MessageSource messageSource) {
        this.userService = userService;
        this.messageSource = messageSource;
    }

    @GetMapping("/settings")
    public String getSettings(Principal principal, Model model) {
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());
        model.addAttribute("currentLanguage", user.getLanguage() == null ? "en" : user.getLanguage());
        return "settings";
    }

    @PostMapping("/settings/password")
    public String changePassword(
        @RequestParam("currentPassword") String currentPassword,
        @RequestParam("newPassword") String newPassword,
        @RequestParam("confirmPassword") String confirmPassword,
        Principal principal,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        User user = userService.getByUsername(principal.getName());
        try {
            userService.changePassword(user, currentPassword, newPassword, confirmPassword);
            redirectAttributes.addFlashAttribute("successMessage",
                translate("settings.password.updated"));
            return "redirect:/settings";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("username", user.getUsername());
            model.addAttribute("currentLanguage", user.getLanguage() == null ? "en" : user.getLanguage());
            model.addAttribute("passwordError", translate(ex.getMessage()));
            return "settings";
        }
    }

    @PostMapping("/settings/language")
    public String changeLanguage(
        @RequestParam("language") String language,
        Principal principal,
        RedirectAttributes redirectAttributes
    ) {
        User user = userService.getByUsername(principal.getName());
        userService.setLanguage(user, language);
        redirectAttributes.addFlashAttribute("successMessage",
            translate("settings.language.updated"));
        return "redirect:/settings";
    }

    private String translate(String key) {
        try {
            return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException ex) {
            return key;
        }
    }
}
```

- [ ] **Step 4: Run tests, verify they pass**

```
./mvnw -Dtest=SettingsControllerTests test
```
Expected: PASS (some tests will fail until Task 12 creates the `settings.html` view — that's fine; this step is verified end-to-end once that template exists. **Proceed to Task 12 before re-running.**)

- [ ] **Step 5: Commit (do not run tests yet — view comes in next task)**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/SettingsController.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/controller/SettingsControllerTests.java
git commit -m "feat(settings): add SettingsController with password + language endpoints"
```

---

### Task 12: Create `settings.html` template

**Files:**
- Create: `src/main/resources/templates/settings.html`

- [ ] **Step 1: Write the template**

Create `src/main/resources/templates/settings.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
<head th:replace="~{fragments/layout :: head('Settings')}"></head>
<body>

<div class="app-layout">
    <nav th:replace="~{fragments/layout :: topnav}"></nav>
    <div id="main-content" class="app-main-content">
        <div class="sh-fade-in">
            <div class="sh-dashboard-shell">
                <div class="sh-explorer-shell">
                    <aside th:replace="~{fragments/sidebar :: sidebar}"></aside>
                    <div id="explorer-detail" class="sh-explorer-detail sh-explorer-detail-content">

                        <div class="sh-admin-page">
                            <div class="sh-admin-header">
                                <h1 class="sh-page-title" th:text="#{settings.title}">Settings</h1>
                                <span class="sh-admin-subtitle"
                                      th:text="#{settings.subtitle(${username})}">Signed in as user</span>
                            </div>

                            <div th:if="${successMessage}" class="sh-alert sh-alert-success sh-admin-alert" role="alert">
                                <iconify-icon icon="lucide:check-circle-2" class="sh-alert-icon"></iconify-icon>
                                <div class="sh-alert-body" th:text="${successMessage}"></div>
                            </div>

                            <!-- Change password card -->
                            <div class="sh-card">
                                <div class="sh-card-header">
                                    <div class="sh-card-header-title" th:text="#{settings.password.title}">Change password</div>
                                </div>
                                <div class="sh-card-body">
                                    <div th:if="${passwordError}" class="sh-alert sh-alert-danger" role="alert">
                                        <iconify-icon icon="lucide:alert-circle" class="sh-alert-icon"></iconify-icon>
                                        <div class="sh-alert-body" th:text="${passwordError}"></div>
                                    </div>
                                    <form th:action="@{/settings/password}" method="post" class="sh-form-group-stack">
                                        <div class="sh-login-field">
                                            <label for="currentPassword" class="sh-label" th:text="#{settings.password.current}">Current password</label>
                                            <input type="password" id="currentPassword" name="currentPassword"
                                                   class="sh-input" required autocomplete="current-password">
                                        </div>
                                        <div class="sh-login-field">
                                            <label for="newPassword" class="sh-label" th:text="#{settings.password.new}">New password</label>
                                            <input type="password" id="newPassword" name="newPassword"
                                                   class="sh-input" required autocomplete="new-password">
                                        </div>
                                        <div class="sh-login-field">
                                            <label for="confirmPassword" class="sh-label" th:text="#{settings.password.confirm}">Confirm new password</label>
                                            <input type="password" id="confirmPassword" name="confirmPassword"
                                                   class="sh-input" required autocomplete="new-password">
                                        </div>
                                        <div class="sh-login-submit">
                                            <button type="submit" class="sh-btn sh-btn-primary" th:text="#{settings.password.submit}">Update password</button>
                                        </div>
                                    </form>
                                </div>
                            </div>

                            <!-- Language card -->
                            <div class="sh-card">
                                <div class="sh-card-header">
                                    <div class="sh-card-header-title" th:text="#{settings.language.title}">Language</div>
                                </div>
                                <div class="sh-card-body">
                                    <form th:action="@{/settings/language}" method="post" class="sh-form-group-stack">
                                        <label class="sh-login-field" style="display:flex;align-items:center;gap:0.5rem;">
                                            <input type="radio" name="language" value="de"
                                                   th:checked="${currentLanguage == 'de'}">
                                            <span th:text="#{settings.language.de}">Deutsch</span>
                                        </label>
                                        <label class="sh-login-field" style="display:flex;align-items:center;gap:0.5rem;">
                                            <input type="radio" name="language" value="en"
                                                   th:checked="${currentLanguage == 'en'}">
                                            <span th:text="#{settings.language.en}">English</span>
                                        </label>
                                        <div class="sh-login-submit">
                                            <button type="submit" class="sh-btn sh-btn-primary" th:text="#{settings.language.submit}">Save language</button>
                                        </div>
                                    </form>
                                </div>
                            </div>

                        </div>

                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<div id="modal-placeholder"></div>
<div th:replace="~{fragments/layout :: scripts}"></div>
</body>
</html>
```

- [ ] **Step 2: Run SettingsController tests**

```
./mvnw -Dtest=SettingsControllerTests test
```
Expected: all 4 tests PASS.

- [ ] **Step 3: Run full test suite**

```
./mvnw test
```
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/settings.html
git commit -m "feat(settings): add settings page template with password + language cards"
```

---

### Task 13: Replace username with Settings link in navbar + bottom-sheet

**Files:**
- Modify: `src/main/resources/templates/fragments/layout.html`

- [ ] **Step 1: Modify the desktop topnav**

In `fragments/layout.html`, replace the `<span class="topnav-user">` block (around lines 101–104):

```html
            <a href="/settings" class="topnav-user" th:title="#{nav.settings}">
                <iconify-icon icon="lucide:settings"></iconify-icon>
                <span th:text="#{nav.settings}">Settings</span>
            </a>
```

- [ ] **Step 2: Modify the mobile drawer user pill**

In the same file, replace the `<span class="topnav-mobile-user">` block (around lines 180–183):

```html
    <a href="/settings" class="topnav-mobile-link">
        <iconify-icon icon="lucide:settings"></iconify-icon>
        <span th:text="#{nav.settings}">Settings</span>
    </a>
```

- [ ] **Step 3: Modify the mobile bottom-sheet user pill**

In the same file, replace the `<div class="sh-sheet-user-pill">` block (around lines 281–284):

```html
                <a href="/settings" class="sh-sheet-row-link">
                    <iconify-icon icon="lucide:settings"></iconify-icon>
                    <span th:text="#{nav.settings}">Settings</span>
                    <iconify-icon icon="lucide:chevron-right" class="sh-sheet-arrow"></iconify-icon>
                </a>
```

- [ ] **Step 4: Run UI regression tests**

```
./mvnw -Dtest=UiResourceRegressionTests test
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/fragments/layout.html
git commit -m "feat(nav): replace username with Settings link in topnav, drawer, bottom-sheet"
```

---

## Phase 3 — i18n bridge

### Task 14: Create `fragments/i18n-bridge.html`

**Files:**
- Create: `src/main/resources/templates/fragments/i18n-bridge.html`

- [ ] **Step 1: Write the fragment**

Create `src/main/resources/templates/fragments/i18n-bridge.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
<script th:fragment="bridge" th:inline="javascript">
    window.i18n = /*[[${i18nJs}]]*/ {};
</script>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/fragments/i18n-bridge.html
git commit -m "feat(i18n): add i18n-bridge fragment to expose translations to JS"
```

---

### Task 15: Build `i18nJs` model attribute in `GlobalControllerAdvice`

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/GlobalControllerAdvice.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/controller/GlobalControllerAdviceI18nJsTests.java`

- [ ] **Step 1: Add a sample `js.*` key to bundle for the test**

Append to `src/main/resources/messages.properties`:

```
# Sample JS-side translation (real entries added in Phase 4)
js.test.hello=Hello
```

Append to `src/main/resources/messages_de.properties`:

```
js.test.hello=Hallo
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/com/HendrikHoemberg/StudyHelper/controller/GlobalControllerAdviceI18nJsTests.java`:

```java
package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.entity.UserRole;
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GlobalControllerAdviceI18nJsTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUser() {
        userRepository.findByUsername("i18n-en-user").ifPresent(userRepository::delete);
        userRepository.findByUsername("i18n-de-user").ifPresent(userRepository::delete);
        User en = new User();
        en.setUsername("i18n-en-user");
        en.setPassword(passwordEncoder.encode("password1"));
        en.setRole(UserRole.USER);
        en.setEnabled(true);
        en.setLanguage("en");
        userRepository.save(en);

        User de = new User();
        de.setUsername("i18n-de-user");
        de.setPassword(passwordEncoder.encode("password1"));
        de.setRole(UserRole.USER);
        de.setEnabled(true);
        de.setLanguage("de");
        userRepository.save(de);
    }

    @Test
    void englishUser_PageContainsEnglishI18nDict() throws Exception {
        mockMvc.perform(get("/settings").with(user("i18n-en-user")))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"test.hello\":\"Hello\"")));
    }

    @Test
    void germanUser_PageContainsGermanI18nDict() throws Exception {
        mockMvc.perform(get("/settings").with(user("i18n-de-user")))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"test.hello\":\"Hallo\"")));
    }
}
```

- [ ] **Step 3: Run tests, verify they fail**

```
./mvnw -Dtest=GlobalControllerAdviceI18nJsTests test
```
Expected: FAIL — no `i18nJs` attribute, no bridge fragment in layout, no JSON in response body.

- [ ] **Step 4: Add `i18nJs` model attribute**

Modify `GlobalControllerAdvice.java`. Add imports:

```java
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.support.AbstractResourceBasedMessageSource;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.lang.reflect.Method;
```

Add field + constructor param. Update the constructor signature to accept `MessageSource messageSource` (append to the end of the constructor parameter list), assign `this.messageSource = messageSource;` at the bottom of the constructor body, and add a private final field `private final MessageSource messageSource;` near the other fields.

Add the new `@ModelAttribute` method:

```java
    @ModelAttribute("i18nJs")
    public Map<String, String> addI18nJs() {
        Locale locale = LocaleContextHolder.getLocale();
        Map<String, String> result = new HashMap<>();
        if (!(messageSource instanceof ReloadableResourceBundleMessageSource rms)) {
            return result;
        }
        Properties merged = loadMergedProperties(rms, locale);
        merged.stringPropertyNames().forEach(key -> {
            if (key.startsWith("js.")) {
                String stripped = key.substring("js.".length());
                String value = merged.getProperty(key);
                if (value != null) result.put(stripped, value);
            }
        });
        return result;
    }

    private Properties loadMergedProperties(ReloadableResourceBundleMessageSource rms, Locale locale) {
        Properties merged = new Properties();
        addPropertiesForBasename(rms, "messages", Locale.ENGLISH, merged);
        if (!Locale.ENGLISH.getLanguage().equals(locale.getLanguage())) {
            addPropertiesForBasename(rms, "messages", locale, merged);
        }
        return merged;
    }

    private void addPropertiesForBasename(ReloadableResourceBundleMessageSource rms,
                                          String basename, Locale locale, Properties target) {
        try {
            Method m = AbstractResourceBasedMessageSource.class
                .getDeclaredMethod("getMergedProperties", Locale.class);
            m.setAccessible(true);
            Object holder = m.invoke(rms, locale);
            Method propsMethod = holder.getClass().getMethod("getProperties");
            propsMethod.setAccessible(true);
            Properties loaded = (Properties) propsMethod.invoke(holder);
            if (loaded != null) target.putAll(loaded);
        } catch (NoSuchMessageException ignored) {
            // missing locale file is fine
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load merged messages for " + locale, ex);
        }
    }
```

Note: the reflection above relies on `ReloadableResourceBundleMessageSource.getMergedProperties` which is package-private. If maintenance breaks this, swap to a simpler approach: iterate a known list of keys by calling `messageSource.getMessage(key, ...)` for each. The reflection approach is preferred because it stays in sync with whatever `js.*` keys we add without a hard-coded list.

- [ ] **Step 5: Wire the bridge fragment into `layout.html`**

Modify `src/main/resources/templates/fragments/layout.html`. In the `<div th:fragment="scripts">` block, add immediately after the htmx script tag and before `app.js`:

```html
    <div th:replace="~{fragments/i18n-bridge :: bridge}"></div>
```

So the relevant lines look like:

```html
    <script src="https://code.iconify.design/iconify-icon/2.1.0/iconify-icon.min.js"></script>
    <script src="https://unpkg.com/htmx.org@2.0.4"></script>
    <div th:replace="~{fragments/i18n-bridge :: bridge}"></div>
    <script src="/js/app.js"></script>
```

- [ ] **Step 6: Run tests, verify they pass**

```
./mvnw -Dtest=GlobalControllerAdviceI18nJsTests test
```
Expected: both tests PASS.

- [ ] **Step 7: Run full suite**

```
./mvnw test
```
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/GlobalControllerAdvice.java \
        src/main/resources/templates/fragments/layout.html \
        src/main/resources/messages.properties \
        src/main/resources/messages_de.properties \
        src/test/java/com/HendrikHoemberg/StudyHelper/controller/GlobalControllerAdviceI18nJsTests.java
git commit -m "feat(i18n): build i18nJs map and inject via bridge fragment in layout"
```

---

### Task 16: Add `nav.*` keys for the topnav + drawer + bottom-bar

**Files:**
- Modify: `src/main/resources/messages.properties`
- Modify: `src/main/resources/templates/fragments/layout.html`

- [ ] **Step 1: Add nav keys to bundle**

Append to `messages.properties`:

```
# Navigation labels
nav.dashboard=Dashboard
nav.study=Study Session
nav.study-short=Study
nav.ai-flashcards=AI Flashcards
nav.ai-short=AI
nav.past-exams=Past Exams
nav.admin=Admin
nav.sign-out=Sign out
nav.folders=Folders
nav.more=More
nav.toggle-theme=Toggle theme
nav.app-theme=App Theme
nav.your-limits=Your Limits
nav.quota.storage=Storage
nav.quota.ai=AI
nav.quota.ai-today=AI today
nav.quota.ai-requests=AI Requests
nav.admin-panel=Admin Panel
nav.show-folders=Show folders
```

- [ ] **Step 2: Replace literals in `layout.html`**

In `src/main/resources/templates/fragments/layout.html`:

- Replace all the topnav link labels (between `<iconify-icon ...></iconify-icon>` and `</a>` for Dashboard / Study Session / AI Flashcards / Past Exams / Admin) with `<span th:text="#{nav.dashboard}">Dashboard</span>`, `<span th:text="#{nav.study}">Study Session</span>`, etc. The literal `Dashboard`, `Study Session`, `AI Flashcards`, `Past Exams`, `Admin` become `th:text` references.
- Replace `Sign out` button text with `<span th:text="#{nav.sign-out}">Sign out</span>`.
- Replace `Folders` button text in the desktop `topnav-folders-btn` with `<span th:text="#{nav.folders}">Folders</span>`.
- Replace `title="Show folders"` with `th:title="#{nav.show-folders}"`.
- Replace `title="Toggle theme"` with `th:title="#{nav.toggle-theme}"`.
- Replace all mobile drawer link texts (Dashboard, Study Session, AI Flashcards, Past Exams, Admin, Sign out, Toggle theme) with `th:text` references using the same keys.
- Replace the bottom-bar labels (`Dashboard`, `Study`, `AI`, `Folders`, `More`) with `th:text="#{nav.dashboard}"`, `th:text="#{nav.study-short}"`, `th:text="#{nav.ai-short}"`, `th:text="#{nav.folders}"`, `th:text="#{nav.more}"`.
- Replace bottom-sheet section titles (`Your Limits`) and quota labels (`Storage`, `AI Requests`) with `th:text="#{nav.your-limits}"`, `th:text="#{nav.quota.storage}"`, `th:text="#{nav.quota.ai-requests}"`. Drawer quota labels (`Storage`, `AI today`) become `th:text="#{nav.quota.storage}"` and `th:text="#{nav.quota.ai-today}"`. Desktop `Storage`/`AI` quota labels become `th:text="#{nav.quota.storage}"`/`th:text="#{nav.quota.ai}"`.
- Replace `App Theme` text with `th:text="#{nav.app-theme}"`.
- Replace `Admin Panel` text in the sheet with `th:text="#{nav.admin-panel}"`.

(For attribute values like `title="..."`, use `th:title` so the original `title=` attribute is removed when Thymeleaf renders.)

- [ ] **Step 3: Run UI regression tests**

```
./mvnw -Dtest=UiResourceRegressionTests test
```
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/fragments/layout.html \
        src/main/resources/messages.properties
git commit -m "feat(i18n): externalize nav, topnav, drawer, bottom-bar labels"
```

---

## Phase 4 — Extract English strings

> Each task in this phase follows the same pattern: pick a file or small group of files, replace every English literal in the template with `th:text="#{key}"` / `th:placeholder` / `th:title` / `th:aria-label`, add the keys to `messages.properties`, run `./mvnw -Dtest=UiResourceRegressionTests test`, commit.
>
> **Rules for every extraction task:**
> - Use namespaced keys: `<page-or-feature>.<element>.<role>`. E.g. `dashboard.empty-state.title`, `study.wizard.cards-due`.
> - For `placeholder`, `title`, `aria-label`, `alt`: switch to the `th:` variant. Thymeleaf will replace the static attribute.
> - For text inside an element: use `th:text="#{key}"`. Keep the existing English text as the element's body for non-Thymeleaf rendering fallback.
> - For parameterized strings ("5 cards due"): use `th:text="#{key(${value})}"` and the bundle value `key={0} cards due`.
> - For JS-side strings: add the key prefixed with `js.` to the bundle, and replace the JS literal with `window.i18n['<key-without-js-prefix>']` (or `(window.i18n && window.i18n['key']) || 'fallback'` if you want a defensive fallback).
> - After each task, run `./mvnw -Dtest=UiResourceRegressionTests test` to catch typos.
> - Commit after each task.
>
> **Important:** Don't try to translate while extracting. Pass 1 (this phase) only moves English strings to the bundle. Pass 2 (Phase 5) produces the German bundle.

### Task 17: Extract `login.html`, `register.html`, `error.html`

**Files:**
- Modify: `src/main/resources/templates/login.html`
- Modify: `src/main/resources/templates/register.html`
- Modify: `src/main/resources/templates/error.html`
- Modify: `src/main/resources/messages.properties`

- [ ] **Step 1: Read each file, list every English literal**

Read each of the three files end-to-end. Make a list of every user-visible string: page titles, `<h1>` / `<h2>` / `<p>`, `<label>`, `<button>`, `placeholder=`, `title=`, `alt=`, `<a>` link text.

- [ ] **Step 2: Add keys to bundle**

Append a block to `messages.properties`:

```
# Login + Register + Error pages
login.title=Sign in
login.subtitle=Welcome back
login.username=Username
login.password=Password
login.submit=Sign in
login.no-account=Need an account?
login.register-link=Create one
register.title=Register
register.subtitle=Create your account
register.code=Invite code
register.code-placeholder=Enter your invite code
register.username=Username
register.username-placeholder=Choose a username
register.password=Password
register.password-placeholder=Choose a password
register.submit=Create account
register.back-link=Back to sign in
error.title=Something went wrong
error.back=Back to dashboard
```

Adjust keys/values based on what you actually find in the files. For literals not listed above (e.g. flash messages, specific error labels), add them with sensible names.

- [ ] **Step 3: Replace literals in each template**

In each template, replace every English literal with the matching `#{key}` reference. For attribute values use `th:placeholder`, `th:title`, `th:alt`, `th:aria-label`.

For `login.html` and `register.html`, also wrap the `<title>` in `th:text` (these pages don't use `layout :: head`):

```html
<title th:text="#{login.title} + ' — StudyHelper'">Sign in — StudyHelper</title>
```

- [ ] **Step 4: Verify**

```
./mvnw -Dtest=UiResourceRegressionTests test
```
Expected: PASS.

Also run the registration integration test which renders `register.html`:

```
./mvnw -Dtest=RegistrationControllerIntegrationTests test
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/login.html \
        src/main/resources/templates/register.html \
        src/main/resources/templates/error.html \
        src/main/resources/messages.properties
git commit -m "feat(i18n): externalize login/register/error page strings"
```

---

### Task 18: Extract `dashboard.html` + `fragments/explorer.html` + `fragments/explorer-dashboard.html`

**Files:**
- Modify: `src/main/resources/templates/dashboard.html`
- Modify: `src/main/resources/templates/fragments/explorer.html`
- Modify: `src/main/resources/messages.properties`

- [ ] **Step 1: Read the files, list every literal**

`grep -nE '>[A-Z][a-z][^<>{}]*<|placeholder=|title=|alt=|aria-label=' src/main/resources/templates/dashboard.html src/main/resources/templates/fragments/explorer.html` is a useful starting filter, but read each file end-to-end to catch literals it misses (e.g. inside `th:text` strings or `th:if` blocks).

- [ ] **Step 2: Add keys to bundle**

Use the `dashboard.*` namespace for dashboard-specific labels, `explorer.*` for the explorer fragment. Examples:

```
dashboard.title=My Library
dashboard.empty-state.title=Welcome to StudyHelper
dashboard.empty-state.body=Create your first folder to start organizing decks.
dashboard.create-folder=Create folder
explorer.tab.folders=Folders
explorer.tab.decks=Decks
explorer.tab.files=Files
```

(Add every literal you find. Don't guess at keys — derive each from the actual text.)

- [ ] **Step 3: Replace literals**

In each file, swap literals for `th:text` / `th:placeholder` / `th:title`.

- [ ] **Step 4: Verify**

```
./mvnw -Dtest=UiResourceRegressionTests test
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/dashboard.html \
        src/main/resources/templates/fragments/explorer.html \
        src/main/resources/messages.properties
git commit -m "feat(i18n): externalize dashboard + explorer strings"
```

---

### Task 19: Extract `fragments/sidebar.html`, `fragments/tab-bar.html`, `fragments/tab-decks.html`, `fragments/tab-files.html`, `fragments/tab-folders.html`

**Files:**
- Modify each of the listed templates
- Modify: `src/main/resources/messages.properties`

- [ ] **Step 1: For each file, read it, list every literal, add keys to bundle**

Suggested namespaces: `sidebar.*`, `tab.*`. Example keys:

```
sidebar.folders=Folders
sidebar.new-folder=New folder
sidebar.empty=No folders yet
tab.bar.search=Search
tab.decks.empty=No decks here yet.
tab.files.empty=No files here yet.
tab.folders.empty=No subfolders.
```

- [ ] **Step 2: Replace literals**

For each file, swap literals for `th:text` / `th:placeholder` / `th:title` / `th:aria-label`.

- [ ] **Step 3: Verify**

```
./mvnw -Dtest=UiResourceRegressionTests test
```
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/fragments/sidebar.html \
        src/main/resources/templates/fragments/tab-bar.html \
        src/main/resources/templates/fragments/tab-decks.html \
        src/main/resources/templates/fragments/tab-files.html \
        src/main/resources/templates/fragments/tab-folders.html \
        src/main/resources/messages.properties
git commit -m "feat(i18n): externalize sidebar and tab-bar strings"
```

---

### Task 20: Extract folder & file fragments

**Files:**
- Modify: `src/main/resources/templates/folder-page.html`
- Modify: `src/main/resources/templates/fragments/folder-form.html`
- Modify: `src/main/resources/templates/fragments/folder-children.html`
- Modify: `src/main/resources/templates/fragments/folder-detail.html`
- Modify: `src/main/resources/templates/fragments/file-form.html`
- Modify: `src/main/resources/templates/fragments/file-edit-modal.html`
- Modify: `src/main/resources/messages.properties`

- [ ] **Step 1: For each file, read + extract**

Namespace: `folder.*`, `file.*`. Add every literal you find.

- [ ] **Step 2: Verify**

```
./mvnw -Dtest=UiResourceRegressionTests test
```
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/folder-page.html \
        src/main/resources/templates/fragments/folder-form.html \
        src/main/resources/templates/fragments/folder-children.html \
        src/main/resources/templates/fragments/folder-detail.html \
        src/main/resources/templates/fragments/file-form.html \
        src/main/resources/templates/fragments/file-edit-modal.html \
        src/main/resources/messages.properties
git commit -m "feat(i18n): externalize folder + file template strings"
```

---

### Task 21: Extract deck templates

**Files:**
- Modify: `src/main/resources/templates/deck-page.html`
- Modify: `src/main/resources/templates/fragments/deck.html`
- Modify: `src/main/resources/templates/fragments/deck-form.html`
- Modify: `src/main/resources/templates/fragments/flashcard-form.html`
- Modify: `src/main/resources/messages.properties`

- [ ] **Step 1: Extract**

Namespace: `deck.*`, `flashcard.*`. Repeat the read+swap pattern.

- [ ] **Step 2: Verify**

```
./mvnw -Dtest=UiResourceRegressionTests test
```
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/deck-page.html \
        src/main/resources/templates/fragments/deck.html \
        src/main/resources/templates/fragments/deck-form.html \
        src/main/resources/templates/fragments/flashcard-form.html \
        src/main/resources/messages.properties
git commit -m "feat(i18n): externalize deck + flashcard template strings"
```

---

### Task 22: Extract study + quiz fragments

**Files:**
- Modify: `src/main/resources/templates/study-page.html`
- Modify: `src/main/resources/templates/fragments/study-card.html`
- Modify: `src/main/resources/templates/fragments/study-complete.html`
- Modify: `src/main/resources/templates/fragments/study-setup.html`
- Modify: `src/main/resources/templates/fragments/study-tutorials.html`
- Modify: `src/main/resources/templates/fragments/quiz-question.html`
- Modify: `src/main/resources/templates/fragments/quiz-summary.html`
- Modify: `src/main/resources/templates/fragments/saved-session.html`
- Modify: `src/main/resources/messages.properties`

- [ ] **Step 1: Extract**

Namespace: `study.*`, `quiz.*`, `session.*`. Read each file, add keys to bundle, swap literals. Note `study-tutorials.html` is text-heavy.

- [ ] **Step 2: Verify**

```
./mvnw -Dtest=UiResourceRegressionTests test
```
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/study-page.html \
        src/main/resources/templates/fragments/study-card.html \
        src/main/resources/templates/fragments/study-complete.html \
        src/main/resources/templates/fragments/study-setup.html \
        src/main/resources/templates/fragments/study-tutorials.html \
        src/main/resources/templates/fragments/quiz-question.html \
        src/main/resources/templates/fragments/quiz-summary.html \
        src/main/resources/templates/fragments/saved-session.html \
        src/main/resources/messages.properties
git commit -m "feat(i18n): externalize study + quiz template strings"
```

---

### Task 23: Extract exam templates

**Files:**
- Modify: `src/main/resources/templates/exam-page.html`
- Modify: `src/main/resources/templates/exams-page.html`
- Modify: `src/main/resources/templates/fragments/exam-detail.html`
- Modify: `src/main/resources/templates/fragments/exam-grading.html`
- Modify: `src/main/resources/templates/fragments/exam-question.html`
- Modify: `src/main/resources/templates/fragments/exam-result.html`
- Modify: `src/main/resources/templates/fragments/exam-single-page.html`
- Modify: `src/main/resources/templates/fragments/exams-list.html`
- Modify: `src/main/resources/messages.properties`

- [ ] **Step 1: Extract**

Namespace: `exam.*`, `exams.*`. Read each file, add keys, swap literals.

- [ ] **Step 2: Verify**

```
./mvnw -Dtest=UiResourceRegressionTests test
```
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/exam-page.html \
        src/main/resources/templates/exams-page.html \
        src/main/resources/templates/fragments/exam-detail.html \
        src/main/resources/templates/fragments/exam-grading.html \
        src/main/resources/templates/fragments/exam-question.html \
        src/main/resources/templates/fragments/exam-result.html \
        src/main/resources/templates/fragments/exam-single-page.html \
        src/main/resources/templates/fragments/exams-list.html \
        src/main/resources/messages.properties
git commit -m "feat(i18n): externalize exam template strings"
```

---

### Task 24: Extract wizard + flashcard-generator + quota + dialog + lightbox + pdf templates

**Files:**
- Modify: `src/main/resources/templates/flashcard-generator-page.html`
- Modify: `src/main/resources/templates/fragments/flashcard-generator.html`
- Modify: `src/main/resources/templates/fragments/ai-generation-error.html`
- Modify: `src/main/resources/templates/fragments/wizard-exam.html`
- Modify: `src/main/resources/templates/fragments/wizard-flashcards.html`
- Modify: `src/main/resources/templates/fragments/wizard-quiz.html`
- Modify: `src/main/resources/templates/fragments/wizard-source-picker.html`
- Modify: `src/main/resources/templates/fragments/quota.html`
- Modify: `src/main/resources/templates/fragments/dialog.html`
- Modify: `src/main/resources/templates/fragments/lightbox.html`
- Modify: `src/main/resources/templates/fragments/pdf-viewer.html`
- Modify: `src/main/resources/templates/fragments/pdf-splitter.html`
- Modify: `src/main/resources/templates/fragments/color-picker.html`
- Modify: `src/main/resources/templates/fragments/image-editor.html`
- Modify: `src/main/resources/messages.properties`

- [ ] **Step 1: Extract**

Namespaces: `wizard.*`, `flashcard-gen.*`, `quota.*`, `dialog.*`, `lightbox.*`, `pdf.*`, `color-picker.*`, `image-editor.*`. Read each file, add keys, swap literals.

- [ ] **Step 2: Verify**

```
./mvnw -Dtest=UiResourceRegressionTests test
```
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/flashcard-generator-page.html \
        src/main/resources/templates/fragments/flashcard-generator.html \
        src/main/resources/templates/fragments/ai-generation-error.html \
        src/main/resources/templates/fragments/wizard-exam.html \
        src/main/resources/templates/fragments/wizard-flashcards.html \
        src/main/resources/templates/fragments/wizard-quiz.html \
        src/main/resources/templates/fragments/wizard-source-picker.html \
        src/main/resources/templates/fragments/quota.html \
        src/main/resources/templates/fragments/dialog.html \
        src/main/resources/templates/fragments/lightbox.html \
        src/main/resources/templates/fragments/pdf-viewer.html \
        src/main/resources/templates/fragments/pdf-splitter.html \
        src/main/resources/templates/fragments/color-picker.html \
        src/main/resources/templates/fragments/image-editor.html \
        src/main/resources/messages.properties
git commit -m "feat(i18n): externalize wizard, generator, modals, and other fragment strings"
```

---

### Task 25: Extract admin templates + remaining fragments

**Files:**
- Modify: `src/main/resources/templates/admin.html`
- Modify: any remaining template files in `src/main/resources/templates/fragments/` not yet covered (verify with `git status` after Phase 4 tasks)
- Modify: `src/main/resources/messages.properties`

- [ ] **Step 1: List remaining files**

```
git status --porcelain src/main/resources/templates/
```
This shows which templates still have uncommitted English (none should be left in modified state; the list above is the master).

```
ls src/main/resources/templates/fragments/
```
Compare against the files touched in Tasks 16, 17, 19, 20, 21, 22, 23, 24. Anything not yet touched gets extracted here.

- [ ] **Step 2: Extract**

Namespace: `admin.*`. Add keys, swap literals.

- [ ] **Step 3: Verify**

```
./mvnw test
```
Expected: PASS (including the admin controller tests that render `admin.html`).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/admin.html \
        src/main/resources/messages.properties
git commit -m "feat(i18n): externalize admin template strings"
```

---

### Task 26: Extract JS-side strings into `js.*` keys

**Files:**
- Modify: `src/main/resources/static/js/app.js`
- Modify: `src/main/resources/static/js/study-wizard.js`
- Modify: `src/main/resources/static/js/exam.js`
- Modify: `src/main/resources/static/js/pdf-viewer.js`
- Modify: `src/main/resources/static/js/pdf-splitter.js`
- Modify: `src/main/resources/static/js/image-editor.js`
- Modify: `src/main/resources/static/js/color-picker.js`
- Modify: `src/main/resources/messages.properties`

- [ ] **Step 1: For each JS file, find user-visible strings**

`grep -nE "alert\(|confirm\(|innerHTML\s*=|textContent\s*=|placeholder\s*=" src/main/resources/static/js/<file>.js` finds candidates. Then read the file to catch dynamically-built modal labels, button text, error strings.

- [ ] **Step 2: Add `js.*` keys to bundle**

Each JS string becomes a `js.<feature>.<role>` key. Examples:

```
js.app.confirm-delete-folder=Delete this folder and all its contents?
js.app.copied=Copied to clipboard
js.study.wizard.next=Next
js.study.wizard.back=Back
js.exam.confirm-submit=Submit exam?
js.pdf.viewer.zoom-in=Zoom in
js.pdf.splitter.parts-empty=No parts selected
js.image-editor.save=Save
js.color-picker.reset=Reset
```

Add real keys based on actual JS strings (not the placeholders above).

- [ ] **Step 3: Replace literals in JS**

Replace each literal with `window.i18n['<key-without-js-prefix>']`. Example:

Before:
```js
alert('Copied to clipboard');
```

After:
```js
alert(window.i18n['app.copied']);
```

For strings used in templates (e.g. `outerHTML = '<div>...Next...</div>'`), prefer constructing with `window.i18n` calls or — better — refactor to a function `t(key) { return window.i18n[key] || key; }` defined once at the top of each JS file, then use `t('study.wizard.next')`.

- [ ] **Step 4: Manual verification**

The `verify` skill applies here. Run the app, open a couple of JS-built modals (PDF splitter, study wizard's next/back), confirm strings still appear (we're still in English).

```
./mvnw spring-boot:run
```

Visit http://localhost:8080 and exercise the modals. The strings should look unchanged.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/js/app.js \
        src/main/resources/static/js/study-wizard.js \
        src/main/resources/static/js/exam.js \
        src/main/resources/static/js/pdf-viewer.js \
        src/main/resources/static/js/pdf-splitter.js \
        src/main/resources/static/js/image-editor.js \
        src/main/resources/static/js/color-picker.js \
        src/main/resources/messages.properties
git commit -m "feat(i18n): externalize JS-side strings under js.* prefix"
```

---

## Phase 5 — German translations

### Task 27: Translate every key to German

**Files:**
- Modify: `src/main/resources/messages_de.properties`

- [ ] **Step 1: Build the German bundle**

Copy every key from `messages.properties` into `messages_de.properties` and translate each value to German. Keep the file ordered identically to `messages.properties` so diffs stay readable.

Translation conventions to follow:
- Address the user formally: `Sie`, not `Du`.
- Capitalize nouns (German orthography rule).
- For UI verbs in buttons, use imperative: `Speichern`, `Anmelden`, `Abbrechen`, `Löschen`.
- Keep brand name `StudyHelper` untranslated.
- Keep `{0}`, `{1}` placeholders.
- For app-specific jargon, prefer the established German term: `Karteikarten` (flashcards), `Stapel` (deck), `Ordner` (folder), `Prüfung` (exam), `Quiz` (quiz), `Lernsitzung` (study session), `Fällig` (due).

Example header block at the top of the file:
```
# UI translations (German). Generated alongside messages.properties.
# Translation conventions:
#   - Formal "Sie" form
#   - Buttons in imperative (Speichern, Abbrechen)
#   - Domain terms: Karteikarten, Stapel, Ordner, Prüfung, Lernsitzung
```

Then every key from `messages.properties` gets a German value:
```
app.brand=StudyHelper
nav.settings=Einstellungen
nav.dashboard=Dashboard
nav.study=Lernsitzung
nav.study-short=Lernen
nav.ai-flashcards=KI-Karteikarten
nav.ai-short=KI
nav.past-exams=Vergangene Prüfungen
nav.admin=Verwaltung
nav.sign-out=Abmelden
nav.folders=Ordner
nav.more=Mehr
nav.toggle-theme=Design umschalten
nav.app-theme=App-Design
nav.your-limits=Ihre Limits
nav.quota.storage=Speicher
nav.quota.ai=KI
nav.quota.ai-today=KI heute
nav.quota.ai-requests=KI-Anfragen
nav.admin-panel=Verwaltungsbereich
nav.show-folders=Ordner anzeigen
settings.title=Einstellungen
settings.subtitle=Angemeldet als {0}
settings.password.title=Passwort ändern
settings.password.current=Aktuelles Passwort
settings.password.new=Neues Passwort
settings.password.confirm=Neues Passwort bestätigen
settings.password.submit=Passwort aktualisieren
settings.password.updated=Passwort aktualisiert.
settings.language.title=Sprache
settings.language.de=Deutsch
settings.language.en=English
settings.language.submit=Sprache speichern
settings.language.updated=Sprache aktualisiert.
errors.password.wrong-current=Das aktuelle Passwort ist falsch.
errors.password.mismatch=Die neuen Passwörter stimmen nicht überein.
errors.password.too-short=Das Passwort muss mindestens 8 Zeichen lang sein.
js.test.hello=Hallo
```

Continue with every key added throughout Phase 4. This is one big edit, but each line is mechanical.

- [ ] **Step 2: Spot-check coverage**

```
diff <(grep -oE '^[a-z][a-z0-9._-]+' src/main/resources/messages.properties | sort -u) \
     <(grep -oE '^[a-z][a-z0-9._-]+' src/main/resources/messages_de.properties | sort -u)
```
Expected: empty diff. Any key in one file but not the other shows up here.

- [ ] **Step 3: Verify the app boots and switches**

```
./mvnw spring-boot:run
```

Visit http://localhost:8080, log in, go to /settings, switch to Deutsch. Confirm:
- Nav labels render in German.
- Dashboard renders in German.
- Settings page itself renders in German.
- Open the study wizard — JS prompts render in German.
- Switch back to English, confirm reversion.

- [ ] **Step 4: Run full test suite**

```
./mvnw test
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/messages_de.properties
git commit -m "feat(i18n): add German translations for all UI strings"
```

---

## Phase 6 — Polish

### Task 28: Locale-aware date formatting in dashboard

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/DashboardController.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DashboardService.java` (audit only — verify whether its `Locale.ROOT` usage is for user-facing formatting)

- [ ] **Step 1: Locate the hard-coded English `DateTimeFormatter`**

`grep -nE 'Locale\.(ENGLISH|US|UK)' src/main/java/com/HendrikHoemberg/StudyHelper/controller/DashboardController.java src/main/java/com/HendrikHoemberg/StudyHelper/service/DashboardService.java`

In `DashboardController.java`, the line:
```java
private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
```
needs to become locale-aware. Since `DateTimeFormatter` is immutable and the constant is shared, replace it with a method that builds a formatter per-call using the request locale.

- [ ] **Step 2: Refactor**

Remove the `MONTH` constant from `DashboardController.java`. Find every usage of `MONTH` in the file. Replace each `MONTH.format(...)` with a helper:

```java
import org.springframework.context.i18n.LocaleContextHolder;

private DateTimeFormatter monthFormatter() {
    return DateTimeFormatter.ofPattern("MMM", LocaleContextHolder.getLocale());
}
```

And usages become `monthFormatter().format(date)`.

For `DashboardService.java`, the `Locale.ROOT` usage is at line 204 (`trimmed.substring(0, end).toUpperCase(Locale.ROOT)`) — this is for internal string normalization, not user-facing. **Leave it alone.**

- [ ] **Step 3: Run dashboard tests**

```
./mvnw -Dtest=DashboardServiceTests test
```
Expected: PASS.

- [ ] **Step 4: Manual verification**

```
./mvnw spring-boot:run
```
Visit /dashboard while logged in as a German-language user, confirm month abbreviations render in German (e.g. `Mai` instead of `May`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/DashboardController.java
git commit -m "feat(i18n): make dashboard month formatting locale-aware"
```

---

### Task 29: End-to-end manual verification

**Files:** (none — verification only)

- [ ] **Step 1: Start the app**

```
./mvnw spring-boot:run
```

- [ ] **Step 2: Walk through both languages**

Log in as an existing user. Run through this checklist:

| Area | Action | English check | German check |
|---|---|---|---|
| Topnav | Click brand → /dashboard | "Dashboard" label visible | "Dashboard" label visible (same) |
| Topnav | Hover Study Session link | "Study Session" label | "Lernsitzung" label |
| Dashboard | Empty state visible? | English | German |
| Sidebar | Open folder | Folder name and counts render correctly in both | |
| Decks | Open a deck | Deck UI in English / German | |
| Study | Start a session | Wizard prompts (JS) translated | |
| Exam | Open exam | Exam UI translated | |
| Settings | Change password (wrong current) | Error message in active language | |
| Settings | Switch to other language | Page reloads in new language; nav matches | |
| Mobile bottom-sheet | Open "More" sheet | Section labels translated | |
| Log out, log in | Verify new password works | | |

- [ ] **Step 3: Browser console**

Open dev tools, check `window.i18n` exists and contains expected keys.

- [ ] **Step 4: If any string is still English when language is German, fix it**

For each missing translation:
- Open the relevant template, replace the literal with `#{key}`.
- Add the key to `messages.properties` (English) and `messages_de.properties` (German).
- Commit: `git commit -m "feat(i18n): externalize missed string for <area>"`.

- [ ] **Step 5: When verification is clean, commit a "verified" marker (or skip)**

No code commit needed if no fixes were applied. Otherwise, the per-fix commits above suffice.

---

### Task 30: Final test run + cleanup

**Files:** (none — verification)

- [ ] **Step 1: Run the entire test suite**

```
./mvnw test
```
Expected: all PASS.

- [ ] **Step 2: Run a clean build**

```
./mvnw clean package -DskipTests=false
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Final git status check**

```
git status
git log --oneline -30
```

Expected: clean working tree; commits trace each phase logically.

- [ ] **Step 4: Done**

Hand off to the user with: "Implementation complete. Settings page lives at `/settings`. Both the password change and language switch work. The app is bilingual; the German translations in `messages_de.properties` are ready for your review — UX phrasing in study/exam/SRS domains may want a native German speaker to sanity-check."

---

## Self-Review

**Spec coverage**:
- Goals: Settings page (Tasks 11–13), password change (Tasks 8, 11), language switch (Tasks 9, 11), Accept-Language fallback for unset preference (Task 4), all UI bilingual (Phases 4–5). ✓
- Non-goals: user-generated content untranslated (not modified — only templates/JS touched). ✓
- Architecture: column added (Task 1), controller (Task 11), resolver (Tasks 4–5), config (Tasks 3, 5), advice extensions (Tasks 6, 15), bridge (Tasks 14–15), navbar swap (Task 13). ✓
- Data flow: each link traced through the tasks. ✓
- Error handling: wrong current (Task 8), mismatch (Task 8), too short (Task 8), invalid language coerced (Task 9), missing key falls back (Task 3 `useCodeAsDefaultMessage=true`). ✓
- Testing: unit tests for `changePassword`, `setLanguage`, `UserLocaleResolver`, integration test for settings page + i18nJs. ✓
- Date formatting note (`Locale.ENGLISH` in DashboardController) addressed in Task 28. ✓

**Placeholder scan**:
- No "TBD", "TODO", or "implement later" in the plan.
- Task 17–25 say "read each file, list every literal" — this is genuinely how the work is done; the bundle keys depend on what's actually in each file. I list example keys but call out that real keys derive from actual literals. This is honest, not a placeholder.
- Task 26 likewise lists example JS keys, with the same caveat.

**Type consistency**:
- `changePassword(User, String, String, String)` — same signature in test (Task 8), service (Task 8), controller (Task 11).
- `setLanguage(User, String)` — same in test (Task 9), service (Task 9), controller (Task 11).
- `UserLocaleResolver(UserRepository)` constructor — same in test (Task 4), resolver impl (Task 4), bean registration (Task 5).
- `getLanguage()` / `setLanguage(String)` from `User` — generated by Lombok `@Getter`/`@Setter`, consistent.
- `i18nJs` model attribute name — same in `GlobalControllerAdvice` (Task 15), i18n-bridge fragment (Task 14), test assertion (Task 15).
- `currentLocale` model attribute — same in advice (Task 6) and layout `<html lang>` script (Task 7).

Plan is internally consistent.
