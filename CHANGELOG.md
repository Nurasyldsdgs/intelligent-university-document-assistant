# Changelog

## Sprint 2 — gap closure

Closes the gaps between the Sprint 2 spec (US-04 course-bound upload, US-05 AI classification, US-06 ZIP import)
and the code. Manual browser checks: [docs/sprint2-manual-test-checklist.md](docs/sprint2-manual-test-checklist.md).

### Added

- **Background AI classification (US-05).** Uploads without a category are stored immediately as `OTHER` with
  `aiStatus = PENDING`; `DocumentProcessingService` classifies them on the AI executor (Groq,
  `openai/gpt-oss-120b`, JSON mode) and stores category, type and the model's confidence. An upload never waits for
  the AI. New `documents.ai_status`: `PENDING`, `PROCESSING`, `DONE`, `FAILED`, `MANUAL`.
- **Classification history with confidence:** `document_classifications` (one row per document).
- **Deadline extraction right after classification:** SYLLABUS and ASSIGNMENT documents are analyzed in the same
  background job; assignments are created for the course and handed out to enrolled students as tasks.
- **Startup recovery:** documents left `PROCESSING` by a restart go back to `PENDING` and are queued again
  (`AiProcessingRecovery`).
- **ZIP import history (US-06):** `batch_imports` records every archive (total vs. imported files, uploader);
  documents link to their import. `GET /api/v1/courses/{id}/imports` (course teacher, ADMIN).
- **Endpoints:** `GET /api/v1/documents/{id}/status` (aiStatus, category, confidence, assignments count),
  `POST /api/v1/documents/{id}/reclassify` (course teacher, ADMIN).
- **Content-based upload validation:** `FileTypeValidator` detects the real type with Apache Tika; a file whose bytes
  do not match its extension is refused ("Содержимое файла не соответствует расширению"); the detected type is stored.
- **Storage abstraction:** `StorageService` with `LocalStorageService` (default) and `S3StorageService`
  (`app.storage.driver=s3`, AWS S3 or MinIO, AWS SDK v2 2.54.20 with the JDK HTTP client).
- **Frontend:** per-file status in the upload panel ("Загружено" → "ИИ определяет тип…" → category · confidence),
  deadline notice with a link to the course's assignments, "Импортировано X из Y файлов" for ZIPs, `CategoryBadge`
  in all document lists, "Переклассифицировать" for teachers.
- **Maven Wrapper 3.3.4** (`backend/mvnw`, `mvnw.cmd`; Maven 3.9.9), works with paths containing spaces.
- **Tests:** 129 backend unit tests, including `Sprint2QaTest` mirroring QA-UPL-04, QA-ML-05, QA-UPL-06.

### Changed

- **Categories:** `SYLLABUS, ASSIGNMENT, LECTURE, EXAM_ANSWERS, REGULATION, OTHER`; the classifier picks only these.
  `TRANSCRIPT` and `THESIS` remain for old rows. `REGULATIONS` rows are renamed to `REGULATION` at startup.
- **AI model:** `openai/gpt-oss-120b`; the spec's `llama-3.3-70b-versatile` is not available to the project's Groq
  key. At startup the backend checks the configured model against `GET /models` and warns if it is missing.
- **AI client:** retries 429 and 5xx (Retry-After, else 2 s / 4 s, `app.ai.max-attempts`, default 3); new `chatJson`.
- **Stable error codes:** every error response carries `code` (66 codes in `ErrorCodes`; `ApiException` cannot be
  created without one). The frontend translates by code first (`CODE_MESSAGES` in `api/axios.js`), then by the old
  message translations, then a generic text. Removed the test endpoints `/api/v1/test/*` (`TestRoleController`).
- **Single upload** (`POST /documents/upload`) now accepts only the supported formats, like the multi-file upload.
- `UploadedDocument.aiClassified` is always `false` (classification happens after the response); kept for clients.

### Security

- **Login brute-force protection:** 5 failed logins per (email, client IP) within 15 min block that pair for 15 min;
  30 failures per IP (any emails) block the IP. An email is never locked globally. The block is checked before the
  password, a success resets the pair, lockouts are audited (`LOGIN_LOCKED`), the answer is 429 with minutes left.
  Client IP = TCP peer unless `app.security.trust-forwarded-for=true` (env `TRUST_FORWARDED_FOR`).
- **Course enrollment only by invitation link:** students can no longer enroll by the (sequential) course id.
  Each course has a secret `invite_code` (128-bit, URL-safe); students join via `/join/{code}`
  (`POST /api/v1/courses/join`), at most 10 wrong codes per user per 10 minutes (then 429), failures audited.
  Teachers/ADMIN read and regenerate the code (`GET /courses/{id}/invite`, `POST /courses/{id}/invite/regenerate`).
- **No teacher self-registration:** registration and Google sign-up always create `ROLE_STUDENT`. "Я преподаватель"
  only records a request (`users.teacher_requested`) that ADMIN approves or rejects in "Заявки преподавателей"
  (`GET /api/v1/admin/teacher-requests`, `POST …/{id}/approve`, `POST …/{id}/reject`, audited).
- **Rule A:** an `EXAM_ANSWERS` document is always `TEACHER_ONLY` — chosen by hand, found by the AI, or already in the
  database (forced at startup).
- **Rule B:** exam answers are never analyzed into assignments (`POST /analyze` → 400, background analysis skips them).
- **Rule C:** students never see a document while the AI is classifying it (`PENDING`/`PROCESSING`) — in single access
  checks, document lists, full-text search and course-wide Q&A.

### Database (applied automatically at startup, safe with existing data)

| Change | How existing rows are handled |
|---|---|
| `documents.ai_status` NOT NULL | Hibernate adds it with `DEFAULT 'DONE'` |
| `documents.batch_import_id` | nullable, `ON DELETE SET NULL` |
| `documents.category` check constraint | rebuilt from the enum; retired values → `OTHER`, `REGULATIONS` → `REGULATION` |
| `documents.type` backfill | set from category where a same-named type exists |
| `document_classifications`, `batch_imports` | new tables |

### Configuration

New (all optional, defaults shown) — see `application.example.yml` and `backend/.env.example`:

| Property | Env variable | Default |
|---|---|---|
| `app.ai.max-attempts` | `AI_MAX_ATTEMPTS` | `3` |
| `app.ai.max-concurrency` | `AI_MAX_CONCURRENCY` | `2` in the example (Groq free tier) |
| `app.storage.driver` | `STORAGE_DRIVER` | `local` |
| `app.storage.s3.bucket` / `region` / `endpoint` / `path-style-access` | `S3_BUCKET` / `S3_REGION` / `S3_ENDPOINT` / `S3_PATH_STYLE_ACCESS` | — / `eu-central-1` / — / `false` |

S3 credentials come from the AWS default chain (real environment variables or `~/.aws/credentials`), not from
`backend/.env`.

### Known limitations

- A document whose classification **FAILED** is shown to students with the uploader's visibility; if it really was
  exam answers, it is not hidden.
- **Reclassifying** a document into `EXAM_ANSWERS` keeps the assignments that were found in it earlier, so their
  titles and deadlines stay visible to students as tasks.
- Course Q&A calls the AI in the request thread (by design); with 429 retries a question can now wait up to ~40 s.
- The upload panel polls one status request per file every 2 s (a 15-file ZIP = 15 requests per tick).
- Not yet verified against a running PostgreSQL in CI: the JPQL queries and startup migrations are covered only by
  starting the app.
