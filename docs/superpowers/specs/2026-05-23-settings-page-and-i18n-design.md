# Settings Page, Password Change, and Bilingual (DE/EN) Support

**Status**: Design approved, ready for implementation planning
**Date**: 2026-05-23

## Overview

Add a `/settings` page where authenticated users can change their password and switch the app language between German and English. Replace the username display in the desktop navbar and the mobile "more" drawer with a "Settings" link. Make the entire application bilingual: extract every user-visible string from Thymeleaf templates and JavaScript files into Spring `MessageSource` bundles, with `messages.properties` (English) and `messages_de.properties` (German) as the two locale files.

## Goals

- Users can change their password from a self-service page.
- Users can switch the UI language between Deutsch and English, with the change taking effect immediately on the page they're viewing.
- Existing users with no stored language preference get an initial locale chosen from their browser's `Accept-Language` header (German if `de*`, English otherwise).
- All UI text in the app — pages, fragments, dynamically-built JS modals, alerts, tutorials — is rendered through the i18n layer.

## Non-Goals

- Translating user-generated content (deck names, flashcard text, file names, etc.). Those stay in whatever language the user typed them.
- Languages other than German and English.
- Session invalidation / forced re-login on password change (out of scope; current session continues to work).
- A separate `UserPreferences` entity. Language lives directly on the `User` row.
- `ChoiceFormat`-based plural handling for every string. Added case-by-case where a particular string needs it; default is simple `{0}` interpolation.
- Translating exception messages, log output, or admin-only screens. (Admin screens *are* translated, but translation of backend logging stays English.)

## Architecture

Three loosely-coupled pieces, delivered together:

### 1. Settings page

- New `SettingsController` exposing:
  - `GET /settings` — renders `settings.html`
  - `POST /settings/password` — handles password change
  - `POST /settings/language` — handles language switch
- New `settings.html` template with two cards: *Change password* and *Language*.
- Standard form posts (no HTMX). On success, redirect back to `/settings` with a flash `successMessage`. On failure, re-render `settings.html` with a translated `errorMessage` above the relevant card.

### 2. Language preference + i18n plumbing

- New column on `users` table: `language VARCHAR(8) NULL`.
  - `NULL` = user has never set a preference → resolver falls back to `Accept-Language`.
  - `'de'` or `'en'` = user's explicit choice.
- New `UserLocaleResolver extends AcceptHeaderLocaleResolver`:
  - If authenticated user has `language` set, use it.
  - Otherwise, take `Accept-Language` and clamp to `{de, en}` (anything else → `en`).
  - Result is cached on the request as an attribute to avoid repeated DB lookups.
- New `I18nConfig` registering:
  - `LocaleResolver` bean (`UserLocaleResolver`)
  - `MessageSource` bean (`ReloadableResourceBundleMessageSource`):
    - basename `classpath:messages`
    - UTF-8 encoding
    - `fallbackToSystemLocale = false`
    - `defaultLocale = Locale.ENGLISH`
    - `useCodeAsDefaultMessage = true` (so missing keys render as the key itself, easier to catch during extraction)
- `GlobalControllerAdvice` extended to expose `currentLocale` (e.g. `"de"` / `"en"`) for template use (e.g. `<html lang="...">`, active-state highlighting).

### 3. Translation extraction + JS i18n bridge

- All UI strings in templates and JS files moved to `messages.properties` and `messages_de.properties`.
- Namespaced keys: `nav.dashboard`, `settings.password.title`, `study.wizard.next`, `errors.password.too-short`, `js.tutorial.study.intro.title`, etc.
- New `fragments/i18n-bridge.html` fragment included in `layout.html`'s scripts fragment:
  ```html
  <script th:fragment="bridge" th:inline="javascript">
      window.i18n = /*[[${i18nJs}]]*/ {};
  </script>
  ```
- `GlobalControllerAdvice` builds `i18nJs` (a `Map<String,String>`) once per request by scanning the active bundle for keys under the `js.*` prefix and stripping the prefix.
- JS modules replace hardcoded English strings with `window.i18n.someKey` lookups.

## Components

### Entity change

`entity/User.java`:
```java
@Column(length = 8)
private String language;  // null | "de" | "en"
```

JPA schema auto-update (the project's existing migration mechanism — same approach used historically for added columns like `dailyAiRequestLimit`) adds the column as nullable. No data migration required; existing rows keep `language = NULL` and get browser-detected locale until they explicitly set one.

### New controller

`controller/SettingsController.java`:
- `GET /settings` — returns `settings` view with model attrs `username`, `currentLanguage`.
- `POST /settings/password` — delegates to `UserService.changePassword(user, current, next, confirm)`. Catches `IllegalArgumentException` and re-renders with translated `errorMessage`. On success: flash `successMessage` (translated "Password updated"), redirect to `/settings`.
- `POST /settings/language` — delegates to `UserService.setLanguage(user, lang)`, redirects to `/settings`.

### UserService additions

```java
void changePassword(User user, String current, String next, String confirm) {
    if (!passwordEncoder.matches(current, user.getPassword()))
        throw new IllegalArgumentException("Current password is incorrect");
    if (!next.equals(confirm))
        throw new IllegalArgumentException("New passwords do not match");
    validatePassword(next);  // reuses existing MIN_PASSWORD_LENGTH check
    user.setPassword(passwordEncoder.encode(next));
    userRepository.save(user);
}

void setLanguage(User user, String lang) {
    String normalized = "de".equals(lang) ? "de" : "en";
    user.setLanguage(normalized);
    userRepository.save(user);
}
```

Error messages thrown from `changePassword` use translation keys (e.g. `"errors.password.wrong-current"`) so the controller can look them up against `MessageSource` for the active locale.

### New `UserLocaleResolver`

```java
public class UserLocaleResolver extends AcceptHeaderLocaleResolver {
    private static final String CACHE_ATTR = "studyhelper.resolvedLocale";
    private final UserRepository userRepository;

    @Override
    public Locale resolveLocale(HttpServletRequest req) {
        Locale cached = (Locale) req.getAttribute(CACHE_ATTR);
        if (cached != null) return cached;

        Locale resolved = resolveFresh(req);
        req.setAttribute(CACHE_ATTR, resolved);
        return resolved;
    }

    private Locale resolveFresh(HttpServletRequest req) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof UserDetails ud) {
            User user = userRepository.findByUsername(ud.getUsername()).orElse(null);
            if (user != null && user.getLanguage() != null) {
                return "de".equals(user.getLanguage()) ? Locale.GERMAN : Locale.ENGLISH;
            }
        }
        Locale browser = super.resolveLocale(req);
        return browser != null && "de".equals(browser.getLanguage())
                ? Locale.GERMAN : Locale.ENGLISH;
    }
}
```

### New `I18nConfig`

```java
@Configuration
public class I18nConfig implements WebMvcConfigurer {
    @Bean
    public LocaleResolver localeResolver(UserRepository userRepository) {
        return new UserLocaleResolver(userRepository);
    }

    @Bean
    public MessageSource messageSource() {
        var ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        ms.setDefaultLocale(Locale.ENGLISH);
        ms.setUseCodeAsDefaultMessage(true);
        return ms;
    }
}
```

### `GlobalControllerAdvice` extensions

Add two model attributes (applied to all controllers):
- `currentLocale` — `"de"` or `"en"`, sourced from `LocaleContextHolder.getLocale()`.
- `i18nJs` — `Map<String,String>` of JS-side translations (keys under `js.*` prefix, prefix stripped). Built by iterating the active bundle once per request.

### Navbar change

In `fragments/layout.html`:
- **Desktop topnav**: replace `<span class="topnav-user">…<span th:text="${username}">…</span></span>` with `<a href="/settings" class="topnav-user" th:title="#{settings.title}"><iconify-icon icon="lucide:settings"></iconify-icon><span th:text="#{settings.title}">Settings</span></a>`. Reuse `.topnav-user` styles or rename to `.topnav-settings` if styling diverges.
- **Mobile bottom-sheet** (`#mobile-more-sheet`): replace the `<div class="sh-sheet-user-pill">` containing the username with an `<a class="sh-sheet-row-link" href="/settings">` showing a settings icon + label.

### `settings.html` template

Standard `layout.html` head + topnav. Body contains `#explorer-detail` with:
- **Card 1 — Change password**:
  - Header: `<h2>#{settings.password.title}</h2>`
  - Three password inputs (`currentPassword`, `newPassword`, `confirmPassword`) with translated labels and placeholders.
  - Submit button.
  - Above the form, `<div th:if="${errorMessage}" class="alert alert-error" th:text="${errorMessage}">` for failures; `<div th:if="${successMessage}" class="alert alert-success">` for success.
- **Card 2 — Language**:
  - Header: `<h2>#{settings.language.title}</h2>`
  - Two radio buttons (`de`, `en`) with translated labels; the current value preselected.
  - Submit button.

### `fragments/i18n-bridge.html`

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

Included in `layout.html`'s scripts fragment **before** `app.js` and any other project JS, so window.i18n is populated before any JS that reads it runs.

## Data Flow

### Request → locale

1. Request arrives → Spring's `LocaleContextResolver` calls `UserLocaleResolver.resolveLocale`.
2. Resolver checks request attribute cache; if miss, looks up authenticated user's `language`; falls back to `Accept-Language` clamped to `{de, en}`; defaults to `en`.
3. Result cached on request; `LocaleContextHolder` carries it for the rest of the request.
4. Thymeleaf `#{key}` lookups + `MessageSource.getMessage()` calls in controllers use this locale.
5. `GlobalControllerAdvice` builds `i18nJs` for the same locale and exposes it as a model attribute.
6. `layout.html` includes the i18n-bridge fragment which renders `window.i18n` into the page.

### Password change

1. User submits `POST /settings/password` with `currentPassword`, `newPassword`, `confirmPassword`.
2. Controller resolves current `User` from security context.
3. Calls `UserService.changePassword(user, current, next, confirm)`.
4. Service verifies current password via `passwordEncoder.matches`, checks `next.equals(confirm)`, calls existing `validatePassword(next)`, encodes + saves.
5. On `IllegalArgumentException`, controller translates the key and re-renders.
6. On success, flash `successMessage`, redirect to `/settings`.

### Language change

1. User submits `POST /settings/language` with `language=de` (or `en`).
2. Controller resolves current `User`, calls `UserService.setLanguage(user, lang)`.
3. Service coerces to `{de, en}`, saves.
4. Redirect to `/settings`.
5. Next request resolves locale via the new stored value; full page renders in the new language.

## Error Handling

| Scenario | Behavior |
|---|---|
| Wrong current password | Re-render `settings.html` with translated `errors.password.wrong-current` above password card; password fields cleared. |
| New / confirm mismatch | Re-render with `errors.password.mismatch`. |
| New password too short | Re-render with `errors.password.too-short` (reuses `UserService.validatePassword`'s existing message, but the controller looks up the translation key instead of using the exception message verbatim). |
| Invalid language value posted | Silently coerced to `en`. |
| Missing translation key | Spring renders the key itself (via `useCodeAsDefaultMessage`). Highly visible in dev, doesn't crash in prod. |
| Anonymous user hits `/settings` | Spring Security redirects to `/login` (existing behavior; no new config needed). |

## Translation Extraction

Done in two passes:

**Pass 1** — Per template/JS file:
- Replace every English literal with `#{key}` reference (templates) or `window.i18n.someKey` lookup (JS).
- Add the English key/value to `messages.properties`.
- After each file, the app remains fully functional in English.

**Pass 2** — Bulk translation:
- Copy every key from `messages.properties` to `messages_de.properties` with German translations.
- Single file diff for the user (you) to review before merge.

**Date/time formatting** — `DashboardController` currently uses `Locale.ENGLISH` explicitly:
```java
private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
```
Replace with `LocaleContextHolder.getLocale()` so months render `Mai` in German contexts. Audit `DashboardService` too (it imports `java.util.Locale`).

**Scope estimate**:
- ~40 fragment templates
- ~10 page templates
- ~7 JS files with user-visible strings

## Testing

### Unit tests
- `UserServiceTest.changePassword`: wrong current → throws; mismatch → throws; too short → throws; success → password hash updated.
- `UserServiceTest.setLanguage`: `"de"` → stored; `"en"` → stored; `"fr"` → coerced to `"en"`; `null` → coerced to `"en"`.
- `UserLocaleResolverTest`: authenticated with `language=de` → `Locale.GERMAN`; authenticated with `language=null` + `Accept-Language: de` → `Locale.GERMAN`; authenticated with `language=null` + `Accept-Language: fr` → `Locale.ENGLISH`; anonymous + `Accept-Language: de` → `Locale.GERMAN`.

### Integration test
- `GET /settings` (authenticated) returns 200, renders both cards.
- `POST /settings/language` with `de`, then `GET /settings` → response body contains German label text (e.g. assert presence of "Passwort ändern").
- `POST /settings/password` with correct current + matching new/confirm → 302 to `/settings`, flash success; subsequent login with new password works.
- `POST /settings/password` with wrong current → 200, re-renders with error.

### Manual verification (per the project's `verify` skill)
- Run the app, log in.
- Visit `/settings`, switch to Deutsch, confirm `/dashboard` renders in German.
- Open the study wizard, confirm wizard prompts render in German.
- Open the PDF splitter / image editor modal (JS-built), confirm strings come from `window.i18n`.
- Change password, log out, log back in with new password.
- Confirm desktop navbar and mobile bottom-sheet show "Einstellungen" / "Settings" (not the username).

## Open Questions Resolved During Brainstorming

- Settings entry point: replace username display with a "Settings" link (desktop topnav + mobile more-drawer).
- Password form: current + new + confirm.
- Default language for users with no preference: `Accept-Language` header, clamped to `{de, en}`.
- Storage: column on `User` entity, nullable (`NULL` = unset → use Accept-Language).
- Apply timing: immediate redirect to `/settings` so user sees the change.
- JS strings: `window.i18n` dictionary injected from Thymeleaf via i18n-bridge fragment.
- Bundle layout: single `messages.properties` + `messages_de.properties`.
- Translation: I'll produce German translations; the user will review `messages_de.properties` before merge.
