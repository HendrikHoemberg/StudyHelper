# StudyHelper — Code Review

A Spring Boot 4.0.6 / Java 21 study app with Thymeleaf + HTMX, JPA/MySQL, and form-based Spring Security. The codebase is small (~2,000 LOC of Java + ~1,200 of templates) but well-structured. Below is an architectural review with concrete refactor candidates.

## 1. Architecture

### What works
- **Clean layering.** Controllers → Services → Repositories → Entities, with a separate `dto/` package for view models. No entities are leaked across HTTP boundaries beyond what Thymeleaf legitimately needs.
- **Lazy-by-default JPA.** Every `@ManyToOne` is `LAZY`; `spring.jpa.open-in-view=false` is set, which is the right default and forces transactional discipline.
- **Cross-cutting sidebar handled correctly.** `GlobalControllerAdvice` injects the folder tree on every request via `@ModelAttribute` — the right pattern.
- **HTMX integration.** Controllers branch on `HX-Request` / `HX-Target` headers and return fragments, keeping the SPA-like UX without a JS framework.
- **Ownership enforcement.** Most queries are scoped through `findByIdAndUser(...)` rather than `findById(...)` + post-hoc check, so it is hard to accidentally serve another user's data.

### Weaknesses
- **Two oversized classes** dominate the codebase: `StudySessionController` (465 lines) and `FolderService` (316 lines). Both have multiple responsibilities and are the natural seams to split first.
- **Redundant denormalization.** `Deck.user` and `FileEntry.user` duplicate ownership that could be derived through `folder.user`. This creates two sources of truth and complicates cascading.
- **No pagination.** `FolderService.getFolderView()` materializes every subfolder, deck, and file into memory. Fine for a demo, a foot-gun at scale.
- **No `@ControllerAdvice` for exceptions.** Each controller catches and translates errors into flash attributes by hand — duplicated and easy to miss.

## 2. Domain Model

5 entities (`User`, `Folder`, `Deck`, `Flashcard`, `FileEntry`) with sensible relationships and `cascade=ALL, orphanRemoval=true` on the parent sides.

Concerns:
- **Mutable collections leak through Lombok `@Getter`.** Callers can call `folder.getSubFolders().add(...)` and bypass JPA lifecycle.
- **No `equals`/`hashCode` override.** Identity equality is the safest default with Hibernate, so this is acceptable — but worth a note if entities ever land in a `HashSet`.
- **`Folder` is self-referential and parent-of-three** (`subFolders`, `decks`, `files`). The cascade chain on delete is wide; combined with manual file-system cleanup in `FolderService.deleteFolder()` (`src/main/java/.../service/FolderService.java:275`), this is a fragile area to test carefully.

## 3. Persistence / N+1 risks

The single most important code-quality issue is the **lazy-init workaround pattern**:

- `DeckService.getDeck()` (`DeckService.java:45-47`)
- `DeckService.getValidatedDecksInRequestedOrder()` (`DeckService.java:86-87`)
- `FolderService.getFolderView()` (`FolderService.java:243`)

These call `deck.getFlashcards().size()` to force initialization inside the transaction. It works, but it is a textbook N+1 — one query per deck. The proper fix is `LEFT JOIN FETCH` in a `@Query`, or a DTO projection. No repository currently uses `@Query` at all; everything is derived.

## 4. Controllers

- **`StudySessionController` (465 lines).** Handles setup form rendering, deck reordering, session creation, card rendering, answer recording, and redo. Should be split — at minimum a `StudySessionRenderer` (HTMX fragment selection) and a thin controller delegating to the existing `StudySessionService`.
- **HTMX fragment names are scattered string literals** (`"library-grid-container"`, `"files-table-container"`, `"setup"`, `"card"`, `"complete"`). Some are hoisted to constants in `StudySessionController`, others aren't. Centralize.
- **Validation is missing on inputs.** `@RequestParam String name` is accepted without `@NotBlank` / `@Size` / `@Pattern`. Folder/deck names, color hex strings, and icon names should at least bound length and reject blanks.
- **`GlobalControllerAdvice` has duplicated import lines** (`GlobalControllerAdvice.java:3-5` and `13-15`) and recompiles the `/folders/(\\d+)` regex on every request. Trivial fixes.

## 5. Services

- **`FolderService` (316 lines)** mixes CRUD, hierarchy traversal, breadcrumb building, aggregated card counting, and on-disk file cleanup. Split into `FolderCrudService` + `FolderHierarchyService` (or move read-side aggregation into a dedicated query/projection layer).
- **Duplicated helpers.** `normalizeDeckIds()` and `buildFolderPath()` exist in `StudySessionController`, `DeckService`, `StudySessionService`, and parts of `FileEntryService`. Extract to a `PathUtils` / `DeckSelection` utility.
- **`StudySessionService` mixes injection styles** — both `@Autowired` and a constructor (`StudySessionService.java:34`). The `@Autowired` is redundant; remove it for consistency.
- **`FileStorageService` throws raw `RuntimeException`** on I/O failures (`FileStorageService.java:38, 40`). Introduce a `FileStorageException` so callers can react meaningfully and so logs are searchable.
- **No structured logging.** I/O failures in controllers are surfaced as flash messages but never logged with a stack trace. Add SLF4J logging at service boundaries.

## 6. Repositories

5 interfaces, all relying on Spring Data derived queries. Clean, but:
- **No `@Query` / fetch joins anywhere** — directly tied to the N+1 workaround above.
- **`findByIdAndDeckUserUsername(...)`** is a long property-traversal name; a `@Query` would read better and resist refactors.

## 7. Security

- **BCrypt + form login + CSRF default-on.** Solid baseline.
- **DataSeeder bakes in `alice/alice123` and `bob/bob123`.** Fine for a demo; should be guarded by a Spring profile (`@Profile("dev")`) so it cannot run in production.
- **Default DB credentials in `application.properties`** are `studyhelper/studyhelper`. Acceptable as `${...}`-overridable defaults but worth a comment.
- **No security headers** (HSTS, CSP, X-Frame-Options beyond Spring defaults), no session-timeout config, no rate limiting on login. Reasonable gaps for an MVP, worth tracking.
- **File upload has no MIME or extension whitelist** and a 100 MB limit. Any authenticated user can upload anything, including HTML that the `view` endpoint serves inline. Add an allowlist and force `Content-Disposition: attachment` for unknown types.

## 8. Tests

Only ~7 tests for ~2,000 LOC:
- Smoke `contextLoads`
- 3 `@WebMvcTest` controller tests for `StudySessionController`
- 4 unit tests for `StudySessionService`

Missing: every other service, every repository, security/authorization, file upload, and any integration test against a real DB (Testcontainers or H2 with full Spring context). The strongest single ROI here is a `@DataJpaTest` suite for the repositories — that's where the lazy-loading bugs will appear.

## 9. Refactor candidates — prioritized

**High**
1. Replace lazy-init workarounds (`getFlashcards().size()`) with `LEFT JOIN FETCH` or DTO projections.
2. Split `StudySessionController` (465 lines) and `FolderService` (316 lines).
3. Extract duplicated `normalizeDeckIds` / `buildFolderPath` into a utility.
4. Add input validation (`@NotBlank`, `@Size`, `@Pattern`) on controller params and a global `@ControllerAdvice` for exception → flash mapping.
5. Whitelist file MIME types; never serve uploaded content `inline` for non-allowlisted types.

**Medium**
6. Introduce a `FileStorageException` and add SLF4J logging at service boundaries.
7. Guard `DataSeeder` with `@Profile("dev")`.
8. Add Spring Data `Pageable` to folder/deck/file listings.
9. Remove the redundant `@Autowired` in `StudySessionService` and the duplicate imports in `GlobalControllerAdvice`.
10. Drop `Deck.user` / `FileEntry.user` denormalization (or document why it stays).

**Low / acceptable**
11. Wrap exposed entity collections (`Collections.unmodifiableList`) or rely on convention.
12. Centralize HTMX fragment IDs in a constants class.
13. Switch `FolderService.sortFiles()` to a Java 14+ expression `switch`.
14. Add Micrometer / Actuator if observability becomes a concern.

## 10. Bottom line

The fundamentals are right: clear layering, sensible JPA defaults, ownership-scoped queries, BCrypt, and a sane HTMX-on-Thymeleaf approach. The main debt is **two large classes**, **pervasive lazy-loading workarounds**, **duplicated helpers**, and **thin test coverage**. None of it is structural — the architecture is sound and the refactors above can be done incrementally without rewriting anything.
