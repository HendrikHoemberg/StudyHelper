# Admin Panel, Invite Registration, and User Quotas Design

## Context

StudyHelper is a Spring Boot, Thymeleaf, HTMX application with Spring Security form login. The current app already stores users in the database, seeds two users from environment variables, and associates core data with a `User`: folders, decks, flashcards, files, and exams.

The current login model is intentionally small: users have a username and password, and all authenticated users receive the same Spring Security `USER` role. File uploads are stored on disk through `FileStorageService`, and `FileEntry` rows already track `fileSizeBytes`. Flashcard images are stored by filename but do not currently track image sizes.

The goal is to open the app to a small trusted group while keeping registration invite-only and protecting limited VPS storage and Gemini free-tier usage.

## Goals

- Add an admin panel accessible only to the owner's account.
- Let the admin generate one-time registration codes.
- Let invited people register themselves with a username and password.
- Apply a default `1 GiB` storage quota to users, editable in admin.
- Count both folder file uploads and flashcard images against storage quota.
- Apply a default daily AI request quota of `100` requests per user, editable in admin.
- Let the admin view users, edit quotas, disable or enable users, and hard-delete disabled users and their data.
- Preserve the existing simple login flow and user-owned data isolation.

## Non-Goals

- No public open registration without an invite code.
- No email delivery of invites.
- No password reset flow.
- No multi-admin management in the first version.
- No full audit log beyond user/code timestamps and action feedback.
- No global Gemini request cap in the first version.

## Chosen Approach

Use a database-backed admin and invite system.

The existing `User` entity remains the source of authentication. It gains role, enabled-state, storage quota, AI quota, and timestamp fields. A new registration-code entity stores hashed invite codes with usage, expiration, and revocation metadata. Admin routes are restricted through Spring Security by role. Public registration is added at `/register` and requires a valid one-time code.

This approach keeps admin state visible in the app, supports future user management without environment-file changes, and gives the VPS predictable storage controls.

## User Model

Extend `User` with:

- `role`: `USER` or `ADMIN`.
- `enabled`: boolean, default `true`.
- `storageQuotaBytes`: long, default `1073741824` bytes (`1 GiB`).
- `dailyAiRequestLimit`: integer, default `100`.
- `createdAt`: timestamp for admin display.

Startup seeding keeps using the existing environment variables. The account named by `APP_USER1_NAME` is promoted to `ADMIN` on startup. Existing users without quota or role values receive the default `USER` role, enabled state, `1 GiB` storage quota, and `100` daily AI requests.

The admin account still has a configurable storage and AI quota. It is not unlimited by default.

## Security Rules

- `/login`, `/register`, and static assets are public.
- `/admin/**` requires `ADMIN`.
- Other application routes require authentication.
- Disabled users cannot log in.
- Disabled users with active sessions are rejected on their next request and redirected or logged out.
- The admin cannot disable or delete their own account from the admin panel.

`UserDetailsServiceImpl` should expose the user's actual role and enabled state to Spring Security.

## Registration Codes

Add a `RegistrationCode` entity with:

- `id`
- `codeHash`
- `createdAt`
- `expiresAt`
- `usedAt`
- `revokedAt`
- `createdBy`
- `usedBy`

The raw code is generated securely and shown only once after creation. Only a hash is stored. Codes are one-time use and expire 3 days after creation.

Code validation requires:

- Matching hash exists.
- Code is not expired.
- Code has not been used.
- Code has not been revoked.

Admin can:

- Generate a new code.
- Copy or view the raw code immediately after generation.
- See unused codes with expiration time.
- Revoke unused codes.
- See used, expired, and revoked code history.

## Registration Flow

The login page includes a visible "Register with invite code" link to `/register`.

The registration page asks for:

- Invite code.
- Username.
- Password.

On submit:

1. Validate the registration code.
2. Validate username uniqueness.
3. Validate password length, with a minimum of 8 characters.
4. Create an enabled `USER` with `1 GiB` storage quota and `100` daily AI requests.
5. Mark the code as used and link it to the new user.
6. Redirect to login with a success message.

User creation and marking the code used must happen in one transaction so the same code cannot be consumed twice.

## Storage Quotas

Storage quota counts:

- Folder files represented by `FileEntry`.
- Flashcard front and back images.

Storage used is computed from database metadata:

- Sum `FileEntry.fileSizeBytes` for the user.
- Add flashcard `frontImageSizeBytes` and `backImageSizeBytes` values for flashcards owned by the user.

Add to `Flashcard`:

- `frontImageSizeBytes`
- `backImageSizeBytes`

Quota checks use delta accounting:

- New folder file: `used + incomingSize <= quota`.
- Replace folder file: `used - oldFileSize + incomingSize <= quota`.
- New flashcard image: `used + incomingSize <= quota`.
- Replace flashcard image: `used - oldImageSize + incomingSize <= quota`.

If quota would be exceeded, the app rejects the operation before writing where possible and shows a clear error message. The existing global multipart limit of `100MB` remains a separate guardrail.

Deleting files, folders, decks, flashcards, and users frees quota by deleting the related database rows and disk files.

## AI Request Quotas

Add a per-user daily AI quota. The default is `100` requests per day and is editable from the admin panel.

Add an `AiRequestUsage` entity with:

- `id`
- `user`
- `usageDate`
- `requestCount`

Before Gemini-backed operations, the app checks today's usage for the user. Covered operations:

- AI flashcard generation.
- AI quiz generation.
- AI exam generation.
- AI exam grading.

If today's count is greater than or equal to `dailyAiRequestLimit`, the app rejects the request before calling Gemini and shows a quota message. If allowed, the app records the request immediately before the Gemini call. This aligns with the existing `spring.ai.retry.max-attempts=1` setting and avoids undercounting failed provider attempts.

Admin can see today's AI usage and edit each user's daily limit.

## Admin Panel

The admin panel lives at `/admin` and follows the current Thymeleaf/HTMX UI style.

### Users

Show:

- Username.
- Role.
- Enabled or disabled state.
- Storage used and storage quota.
- AI requests used today and daily AI limit.
- Created timestamp.

Actions:

- Edit storage quota.
- Edit daily AI request limit.
- Disable user.
- Enable user.
- Hard-delete user.

Hard delete must be visually and behaviorally separate from disable. The target user must be disabled before hard delete is available. The admin account cannot be disabled or deleted.

Promote and demote actions are intentionally omitted in the first version because only `APP_USER1_NAME` should be admin.

### Registration Codes

Show:

- Generate-code form.
- One-time raw code display after generation.
- Unused codes with expiration and revoke action.
- Used, expired, and revoked code history.

### Feedback

Admin actions use normal success and error messages. A full audit log is out of scope.

## Hard Delete Behavior

Hard delete removes:

- User row.
- Folders.
- Decks.
- Flashcards.
- Exams.
- Folder file rows.
- Uploaded folder files from disk.
- Flashcard image files from disk.

Disk cleanup should only delete filenames found in the target user's owned `FileEntry` and `Flashcard` records and should go through `FileStorageService`. If cleanup fails, the admin action should report failure instead of silently leaving files behind.

## Error Handling

- Invalid, expired, used, or revoked registration codes show a generic invalid-code message.
- Duplicate usernames show a specific username-taken message.
- Disabled users cannot log in.
- Disabled active sessions are blocked on the next request.
- Non-admin users cannot access `/admin/**`.
- Storage quota errors preserve the user's location and show a clear flash message.
- AI quota errors use existing AI error rendering paths where practical.
- Replacement uploads can proceed when replacing a larger stored item with a smaller incoming item.

## Testing

Add focused coverage for:

- Public registration page access.
- Registration success with valid one-time code.
- Registration failure for invalid, expired, used, and revoked codes.
- Username uniqueness during registration.
- Admin-only access to `/admin/**`.
- Admin self-protection for disable and delete.
- Disabled-user login failure.
- Disabled active-session next-request blocking.
- Storage quota checks for folder file upload and replacement.
- Storage quota checks for flashcard image upload and replacement.
- AI quota checks for flashcard generation, quiz generation, exam generation, and exam grading.
- Admin quota updates.
- User hard-delete database cleanup and file cleanup interactions.

## Implementation Notes

- Keep quota logic in services, not controllers, so every upload path is protected.
- Use transactional service methods for registration-code consumption and user creation.
- Prefer repository aggregate queries for storage usage instead of scanning the upload directory.
- Existing `spring.jpa.hibernate.ddl-auto=update` can add columns in development, but production should be checked carefully before deployment.
- The design does not require a visual redesign; admin pages should reuse existing app styling and navigation patterns.
