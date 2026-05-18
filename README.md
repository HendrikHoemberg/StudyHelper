# StudyHelper

StudyHelper is a self-hosted study application for organizing learning material, building flashcard decks, generating AI-assisted study content, and running flashcard, quiz, and exam sessions from saved sources.

The app is built with Spring Boot, Thymeleaf, HTMX-style server-rendered interactions, MySQL, and Spring AI with Google GenAI. It includes Docker-based local and production deployment options, upload storage, invite-based registration, per-user quotas, and an admin panel.

## Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Requirements](#requirements)
- [Configuration](#configuration)
- [Run Locally](#run-locally)
- [Run With Docker](#run-with-docker)
- [Production Deployment](#production-deployment)
- [Using The App](#using-the-app)
- [Project Structure](#project-structure)
- [Testing](#testing)
- [Data Storage And Backups](#data-storage-and-backups)
- [Security Notes](#security-notes)
- [Troubleshooting](#troubleshooting)

## Features

### Study library

- Folder-based library for organizing study material.
- Nested folders with configurable colors and icons.
- Deck creation and editing inside folders.
- Flashcards with text and optional images on the front and back.
- File uploads for supported documents and images.
- PDF viewing, thumbnails, download links, and PDF splitting.

### Study modes

- Flashcard sessions from one or more decks.
- Deck-by-deck study mode.
- Quiz sessions from selected decks and supported files.
- Exam sessions from selected decks and supported files.
- Past exam reports with rename and delete actions.
- Session summary and retry flows for incorrect answers.

### AI workflows

- AI flashcard generation from uploaded documents.
- AI quiz generation from selected decks and files.
- AI exam generation and grading.
- Optional custom instructions for generation flows.
- Daily per-user AI request quotas.

### User and admin management

- Form login with Spring Security.
- Invite-code registration.
- Admin-only panel at `/admin`.
- Admin management for invite codes, users, enabled/disabled state, storage quota, and daily AI request limit.
- Optional initial user seeding through environment variables.

### App experience

- Server-rendered Thymeleaf pages and fragments.
- Dynamic interactions through HTMX-compatible markup and JavaScript.
- PWA manifest and installable app metadata.
- Theme toggle.
- Responsive navigation and folder drawer.

## Tech Stack

- Java 21
- Spring Boot 4.0.6
- Spring MVC
- Spring Data JPA
- Spring Security
- Thymeleaf
- htmx-spring-boot-thymeleaf
- Spring AI 2.0.0-M5
- Google GenAI model configured through Spring AI
- MySQL 8.4
- Apache PDFBox
- PDF.js
- Fabric.js
- Maven Wrapper
- Docker and Docker Compose
- Caddy for production reverse proxy and TLS

## Requirements

For local development without Docker:

- Java 21
- MySQL 8.x
- A Google GenAI API key

For Docker-based development:

- Docker
- Docker Compose plugin
- A Google GenAI API key

The Maven Wrapper is included, so a separate Maven installation is not required.

## Configuration

The app reads configuration from `src/main/resources/application.properties` and environment variables.

Create a local `.env` file when using Docker Compose:

```bash
GOOGLE_API_KEY=your-google-genai-api-key

APP_USER1_NAME=admin
APP_USER1_PASSWORD=change-me-with-at-least-8-characters
APP_USER2_NAME=
APP_USER2_PASSWORD=

DB_ROOT_PASSWORD=change-me
DB_PASSWORD=change-me
```

Important variables:

| Variable | Required | Purpose |
| --- | --- | --- |
| `GOOGLE_API_KEY` | Yes | API key used by Spring AI Google GenAI. |
| `SPRING_DATASOURCE_URL` | No | JDBC URL. Defaults to `jdbc:mysql://localhost:3306/studyhelper`. |
| `SPRING_DATASOURCE_USERNAME` | No | Database user. Defaults to `studyhelper`. |
| `SPRING_DATASOURCE_PASSWORD` | No | Database password. Defaults to `studyhelper`. |
| `FILE_UPLOAD_DIR` | No | Upload storage path. Defaults to `/app/uploads`. |
| `APP_USER1_NAME` | No | Optional seeded user. If present, this user is promoted to admin. |
| `APP_USER1_PASSWORD` | No | Password for the first seeded user. Must be at least 8 characters. |
| `APP_USER2_NAME` | No | Optional second seeded user. |
| `APP_USER2_PASSWORD` | No | Password for the second seeded user. Must be at least 8 characters. |
| `DB_ROOT_PASSWORD` | Production compose | MySQL root password for `docker-compose.prod.yml`. |
| `DB_PASSWORD` | Production compose | MySQL application password for `docker-compose.prod.yml`. |

Current application defaults:

- Database schema management: `spring.jpa.hibernate.ddl-auto=update`
- Multipart upload limit: 25 MB per file and request
- Upload directory: `/app/uploads`
- Session timeout: 1 day
- Secure session cookies enabled by default
- Default storage quota: 1 GiB per user
- Default daily AI request limit: 100 requests per user
- Registration codes expire after 3 days
- Login attempts are blocked for 15 minutes after 5 failed attempts from the same client IP

For plain HTTP local development, if the browser does not keep you signed in, set:

```bash
SERVER_SERVLET_SESSION_COOKIE_SECURE=false
SERVER_FORWARD_HEADERS_STRATEGY=none
```

## Run Locally

Start a MySQL database named `studyhelper` with a `studyhelper` user:

```sql
CREATE DATABASE studyhelper;
CREATE USER 'studyhelper'@'%' IDENTIFIED BY 'studyhelper';
GRANT ALL PRIVILEGES ON studyhelper.* TO 'studyhelper'@'%';
FLUSH PRIVILEGES;
```

Then run the application:

```bash
GOOGLE_API_KEY=your-google-genai-api-key \
APP_USER1_NAME=admin \
APP_USER1_PASSWORD=change-me-with-at-least-8-characters \
SERVER_SERVLET_SESSION_COOKIE_SECURE=false \
./mvnw spring-boot:run
```

Open:

```text
http://localhost:8080
```

The seeded `APP_USER1_NAME` account is promoted to admin on startup. If no seeded users are configured, create an admin user another way before relying on invite-code registration, because invite codes are generated from the admin panel.

## Run With Docker

The default Docker Compose file builds the app and starts MySQL:

```bash
docker compose up --build
```

Open:

```text
http://localhost:8080
```

The local compose stack includes:

- `db`: MySQL 8.4 with persistent `db_data` volume.
- `app`: StudyHelper built from the local Dockerfile.
- `uploads`: persistent upload storage mounted at `/app/uploads`.

Stop the stack:

```bash
docker compose down
```

Stop the stack and remove local volumes:

```bash
docker compose down -v
```

Removing volumes deletes the local database and uploaded files.

## Production Deployment

Production deployment is defined in `docker-compose.prod.yml`.

It runs:

- MySQL 8.4 on an internal Docker network.
- StudyHelper on internal and proxy networks.
- Caddy 2 as the public reverse proxy on ports 80 and 443.
- Upload storage on the host at `/srv/studyhelper/uploads`.

Before deployment:

1. Create a production `.env` file with strong values for `GOOGLE_API_KEY`, `DB_ROOT_PASSWORD`, `DB_PASSWORD`, and initial admin credentials.
2. Update `Caddyfile` if the production domain changes.
3. Ensure `/srv/studyhelper/uploads` exists and is writable by the container user.
4. Configure the server firewall to allow SSH, HTTP, and HTTPS.

Start production:

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

View logs:

```bash
docker compose -f docker-compose.prod.yml logs -f app
```

Stop production:

```bash
docker compose -f docker-compose.prod.yml down
```

## Using The App

### First login

Use the seeded admin account configured through `APP_USER1_NAME` and `APP_USER1_PASSWORD`.

After login, the app redirects to `/dashboard`.

### Invite registration

1. Sign in as an admin.
2. Open `/admin`.
3. Generate an invite code.
4. Share the generated code with the new user.
5. The user registers at `/register`.

Invite codes are stored as hashes, can be revoked before use, and expire after 3 days.

### Library workflow

1. Create folders from the dashboard.
2. Create decks inside folders.
3. Add flashcards manually or generate them from documents.
4. Upload supported files to folders.
5. Start study, quiz, or exam sessions from the dashboard, a folder, a deck, or a file.

### Supported uploads

General uploads accept:

- `.pdf`
- `.txt`
- `.md`
- `.png`
- `.jpg`
- `.jpeg`
- `.gif`
- `.webp`

AI document extraction supports:

- `.pdf`
- `.txt`
- `.md`

Uploaded files are checked by extension and by content signature where applicable. Text files must be valid UTF-8.

## Project Structure

```text
.
|-- src/main/java/com/HendrikHoemberg/StudyHelper
|   |-- config        # Spring configuration and startup data initialization
|   |-- controller    # MVC controllers
|   |-- dto           # Request, response, and view models
|   |-- entity        # JPA entities
|   |-- exception     # Application exceptions
|   |-- repository    # Spring Data repositories
|   |-- security      # Login, user details, disabled-user, and rate-limit filters
|   `-- service       # Business logic
|-- src/main/resources
|   |-- static        # CSS, JavaScript, icons, PDF.js, Fabric.js, PWA files
|   |-- templates     # Thymeleaf pages and fragments
|   `-- application.properties
|-- src/test          # Unit and integration-style tests with H2 test config
|-- Dockerfile
|-- docker-compose.yml
|-- docker-compose.prod.yml
|-- Caddyfile
`-- backup.sh
```

## Testing

Run the full test suite:

```bash
./mvnw test
```

The test profile uses H2 in MySQL compatibility mode and stores test uploads under `target/test-uploads`.

Build the application package:

```bash
./mvnw package
```

The Dockerfile packages the app with:

```bash
./mvnw package -DskipTests
```

## Data Storage And Backups

StudyHelper stores state in two places:

- MySQL for users, folders, decks, flashcards, file metadata, exams, quotas, and invite codes.
- The upload directory for physical uploaded files and flashcard images.

Local Docker volumes:

- `db_data`: MySQL data.
- `uploads`: uploaded files.

Production storage:

- MySQL data in the Docker volume `db_data`.
- Uploads on the host at `/srv/studyhelper/uploads`.

The included `backup.sh` script:

- Loads credentials from `.env`.
- Dumps the MySQL database with `mysqldump`.
- Archives `/srv/studyhelper/uploads`.
- Writes backups to `/srv/studyhelper/backups`.
- Deletes backups older than 3 days.

Make it executable:

```bash
chmod +x backup.sh
```

Example cron entry for a nightly 03:00 backup:

```cron
0 3 * * * /path/to/StudyHelper/backup.sh
```

## Security Notes

- All routes except login, registration, static assets, manifest, service worker, favicon, and icons require authentication.
- `/admin/**` requires the `ADMIN` role.
- Passwords are stored with BCrypt.
- Invite codes are stored as SHA-256 hashes.
- Disabled users are blocked after authentication.
- Login attempts are rate-limited by client IP.
- Upload validation checks allowed extensions and file content.
- The production Docker image runs the app as a non-root user.
- Production traffic is intended to terminate TLS at Caddy.
- Keep `.env`, uploaded files, database files, and backups out of source control.

Recommended production hardening:

- Use strong unique database passwords.
- Keep Docker images and host packages updated.
- Use SSH keys and disable SSH password login on the server.
- Monitor disk usage for database, uploads, and backups.
- Consider adding Content Security Policy headers before exposing the app broadly.
- Test backup restoration periodically, not only backup creation.

## Troubleshooting

### The app cannot connect to MySQL

Check the datasource variables:

```bash
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

For Docker Compose, the JDBC URL should point to the Compose service name:

```text
jdbc:mysql://db:3306/studyhelper
```

For local non-Docker MySQL, the default is:

```text
jdbc:mysql://localhost:3306/studyhelper
```

### AI generation fails

Verify `GOOGLE_API_KEY` is set in the environment that starts the app. Also check that the selected source has extractable content and that the user has not reached the daily AI request limit.

### Uploads fail

Check:

- File size is 25 MB or smaller.
- The file extension is supported.
- The file content matches the extension.
- The upload directory exists and is writable.
- The user has enough remaining storage quota.

### Login succeeds but returns to the login page

When running over plain HTTP locally, secure session cookies can prevent the browser from keeping the session. Set:

```bash
SERVER_SERVLET_SESSION_COOKIE_SECURE=false
```

Use secure cookies in production behind HTTPS.

### The first admin account is missing

Set `APP_USER1_NAME` and `APP_USER1_PASSWORD` before startup. The password must be at least 8 characters. On startup, the first seeded user is promoted to admin if present.

### Production Caddy does not issue a certificate

Check that:

- The domain in `Caddyfile` points to the server.
- Ports 80 and 443 are reachable from the internet.
- The `caddy` service is running.
- No other service is already bound to ports 80 or 443.
