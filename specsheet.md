# Specification Sheet: Flashcard Web Application (Spring Boot 4 Edition)

> [!CAUTION]
> **CRITICAL VERSION REQUIREMENT:** This project **MUST** use **Spring Boot 4.0.6**. This version is very recent. Ensure that all generated code, dependency management, and configuration patterns strictly adhere to Spring Boot 4 standards. Do not fallback to Spring Boot 3.x or 2.x patterns from older training data.

## 1. Tech Stack & Environment
- **Backend:** Java Spring Boot 4.0.6
- **Frontend:** Thymeleaf, HTMX, Bootstrap 5 (CSS)
- **Database:** MySQL
- **Infrastructure:** Docker & Docker Compose (Target: Oracle Cloud Free Tier)

---

## 2. Infrastructure & Deployment (Docker)
- [x] Create a `docker-compose.yml` containing two services: `app` (Spring Boot 4) and `db` (MySQL).
- [x] Configure named Docker volumes for the `db` service (data persistence) and a separate volume for uploaded user files.
- [x] Create a `Dockerfile` for the Spring Boot application (using an Eclipse Temurin JDK 21+ or 25 image, as required for SB4).
- [x] Development mode only for now — no HTTPS/reverse proxy configuration yet. Domain and TLS setup deferred.

---

## 3. Database & Entity Design
- [x] **User Entity:** `id`, `username`, `password` (BCrypt hashed).
- [x] **Folder Entity:** `id`, `name`, `color_hex`, `user_id`, `parent_folder_id` (self-referencing for infinite nesting).
- [x] **Deck Entity:** `id`, `name`, `user_id`, `folder_id` (NOT NULL — decks must always belong to a folder).
- [x] **Flashcard Entity:** `id`, `front_text` (TEXT), `back_text` (TEXT), `deck_id`.
- [x] **File Entity:** `id`, `original_filename`, `stored_filename`, `mime_type`, `file_size_bytes`, `uploaded_at`, `folder_id`, `user_id`.

### Deletion Behavior
- Deleting a folder cascades to all child folders, decks, flashcards, and files recursively.
- If the folder (or any of its descendants) is non-empty, the UI must show a prominent warning dialog before confirming deletion.

---

## 4. Security & Authentication
- [x] Integrate `spring-boot-starter-security` (SB4 compatible configuration).
- [x] Implement `UserDetailsService` and form-based login.
- [x] **Strict Privacy:** Every database query for folders, decks, flashcards, and files must include the `user_id` of the authenticated user to prevent cross-user data access.
- [x] **User Seeding:** There is no public registration. Both user accounts are created automatically via a `DataSeeder` CommandLineRunner on first startup. No admin UI is required.

---

## 5. File Uploads & Knowledgebase
- [x] Users can upload files into any folder to build a topic-specific knowledgebase alongside their flashcard decks.
- [x] **Supported file types:** All types (PDF, images, Word docs, plain text, etc.) — no server-side MIME restriction beyond size.
- [x] **File size limit:** 100 MB per file.
- [x] **Storage:** Files are stored on a local Docker volume (e.g. `/app/uploads`). Path on disk is derived from `stored_filename` (UUID-based to avoid collisions).
- [x] **Actions:** Users can view (inline preview where the browser supports it) and download files. No server-side processing or AI generation at this stage.
- [x] Files are displayed within the folder they belong to in the file explorer, alongside decks.

---

## 6. Frontend & Polished UI (HTMX + 3D Animations)
- [x] **SPA Architecture:** Use HTMX for all navigation and CRUD actions to prevent full page reloads.
- [x] **3D Flip Animation (CSS only):** Prepare the CSS for 3D flashcard flip (perspective, preserve-3d, rotateY). The actual flip interaction is implemented in Step 7 (Study Sessions).
- [x] **Visual Polish:**
  - High-quality Bootstrap 5 cards with subtle shadows and rounded corners.
  - Folder and deck `color_hex` is visually applied throughout the UI for personalization.
  - CSS transitions for all state changes (hover effects, folder expansion, etc.).
- [x] **Color Picker:** Folders use a full color picker (HSL/hex input wheel, similar to the VS Code color picker) — not a limited palette.

### File Explorer (Main Dashboard)
- [x] Cascading file-explorer-style layout with expand/collapse for nested folders.
- [x] Each folder displays an aggregated card count (total cards across all nested decks and subfolders).
- [x] **Sorting options:** Name (A→Z, Z→A), Date Created (newest/oldest), Card Count (high→low, low→high).
- [x] **Filtering options:** Search by name (live filter), filter by folder color.
- [x] File sorting within a folder: Name (A→Z, Z→A), Date Uploaded (newest/oldest), File Size (largest/smallest), File Type.

---

## 7. Learning Logic & Study Sessions
### Session Configuration
- [x] The user selects one or more decks from anywhere in their folder tree (cross-folder selection is supported).
- [x] Two top-level study modes:
  - **Deck-by-Deck:** Cards are studied one deck at a time. Sub-option: decks are presented in their selected order OR in a randomized order.
  - **Shuffled:** All cards from all selected decks are combined into one randomized queue.

### Session Flow
- [x] An HTMX endpoint `/session/next` returns the next card fragment, maintaining the 3D flip state.
- [x] Each card shows a **checkmark button** (got it right) and an **X button** (got it wrong) after the answer is revealed.
- [x] Session ends with a **Session Complete screen** showing:
  - Total cards answered
  - Number correct / incorrect
  - Percentage score
  - Option to **redo the session** (same deck selection and mode, reshuffled).
- [x] No persistent per-card progress tracking or spaced repetition at this stage.
