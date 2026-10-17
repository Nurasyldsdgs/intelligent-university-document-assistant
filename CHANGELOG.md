# Changelog

## Sprint 3 — Review of AI results, personal data protection, OCR

US-08 (the teacher reviews what the AI found before students see it), US-09 (personal data is masked for students,
search, Q&A and the AI), US-07 (scanned PDFs and images get their text by OCR), plus the UI audit fixes. Manual
browser checks: [docs/sprint3-manual-test-checklist.md](docs/sprint3-manual-test-checklist.md).

### Added

- **AI drafts (US-08).** Assignments and deadlines the AI finds in a syllabus or assignment are created as `DRAFT`:
  students do not see them and get no tasks until the course teacher publishes them. New status `DRAFT` next to
  `ACTIVE` and `CLOSED`. `app.ai.auto-publish-assignments=true` restores the Sprint 2 behavior (published at once).
- **Review endpoints** (course teacher or ADMIN, `MANAGE_ASSIGNMENTS`):
  - `GET /api/v1/courses/{id}/assignments/drafts`;
  - `POST /api/v1/assignments/{id}/publish` and `POST /api/v1/courses/{id}/assignments/publish` (several drafts, all
    or nothing; tasks are handed out once);
  - `PATCH /api/v1/assignments/{id}` (title, requirements, deadline; for `DRAFT`, `ACTIVE` and `CLOSED`);
  - `POST /api/v1/assignments/{id}/close` and `POST …/reopen`;
  - `POST /api/v1/assignments/{id}/not-duplicate`.
- **Edits reach the students' tasks without touching their progress:** title, description and deadline are copied
  into the tasks; statuses and submissions stay. A deadline change of a published assignment is audited with the old
  and new value (`ASSIGNMENT_DEADLINE_CHANGED`) and reported to `AssignmentEvents` (notifications follow in Sprint 4).
  An edited, closed or reopened assignment is "taken over" by the teacher: a later re-analysis of its document never
  changes, duplicates or removes it.
- **Duplicate drafts are marked, never merged:** a new draft that looks like an assignment already in the course
  (the same normalized title, or "Лаб. 1" vs "Лабораторная работа №1" with deadlines at most a day apart; different
  numbers never match) is flagged
  "Похоже на …". The teacher deletes it or clears the mark ("Это другое задание").
- **Masked copy of every text (US-09):** IIN, Kazakh phone numbers, e-mails and ID card / passport numbers are
  replaced with `[ИИН скрыт]`, `[телефон скрыт]`, `[e-mail скрыт]`, `[номер документа скрыт]` in
  `documents.masked_text`. The course teacher's e-mail and e-mails on `app.privacy.public-email-domains` stay readable.
  The AI (classification, deadline extraction, Q&A), snippets, Q&A chunks and full-text search use only the masked
  copy; file names are masked in everything sent to the AI. The original stays in `extracted_text`.
- **Text tab:** `GET /api/v1/documents/{id}/text` returns the original text to the course teacher and ADMIN and the
  masked text to everyone else. "Личные данные: N" badge on documents with personal data.
- **Original files with personal data:** a student gets 403 `PII_ORIGINAL_RESTRICTED` on view and download unless the
  course teacher allowed it (`POST /api/v1/documents/{id}/allow-original`, `…/deny-original`). Every opening of such an
  original, by the teacher, ADMIN or an allowed student, is audited (`PII_ORIGINAL_VIEWED`).
- **OCR for scans (US-07):** a PDF or image with less than 100 characters of text is queued for Tesseract (`rus+kaz+eng`,
  first 20 pages, 120 s timeout). OCR runs only in the background job: OCR → masking and Q&A chunks → classification
  → deadline drafts. The upload never runs it. A failure, a timeout or an empty result keeps the document and marks it
  `ocr_status = FAILED`. Tesseract and its languages are checked once at startup; documents waiting for OCR are queued
  again after a restart.
- **"Нужна проверка" for failed scans** (course teacher or ADMIN, `MANAGE_COURSE`):
  - `POST /api/v1/documents/{id}/category` ("Указать тип вручную"): same rules as an AI result;
  - `POST /api/v1/documents/{id}/ocr/retry` ("Повторить распознавание"): a type the AI chose is chosen again from the
    new text; a type chosen by hand stays.
  - Errors: 409 `DOCUMENT_BEING_PROCESSED`, 409 `OCR_NOT_FAILED`, 503 `OCR_UNAVAILABLE`.
- **Frontend:**
  - "Черновики от ИИ" on the course page (edit, delete, publish, publish all, duplicate mark);
  - "Изменить", "Закрыть задание" and "Открыть снова" on assignments;
  - the "Текст" tab, `PiiBadge`, the restricted-original notice and the "Разрешить студентам оригинал" switch;
  - "Распознаём скан…" while OCR runs, "Распознано со скана", the "Нужна проверка" panel and `SetCategoryDialog`;
  - the upload notice counts drafts for the teacher.
- **UI audit:**
  - responsive navbar with a burger menu and a user menu;
  - confirm dialogs for destructive actions;
  - one date format everywhere (`utils/dates.js`);
  - dialog focus handling (`useDialogFocus`), accessible labels, contrast fixes.
- **README:** section "OCR (распознавание сканов)" with the Windows setup of Tesseract (UB Mannheim, Russian and
  Kazakh language data).
- **Tests:** 364 backend tests (15 opt-in ones skipped by default). `Sprint3QaTest` mirrors QA-AI-008, QA-SEC-009 and
  QA-OCR-007 end to end, with the AI and Tesseract mocked and every request to the AI checked for personal data.

### Changed

- **Student work is never destroyed by teacher or admin actions.** An assignment whose students already worked on it
  (status changed or file handed in) is closed instead of deleted. This covers re-analysis, deleting its document and
  deleting it by hand (409 `ASSIGNMENT_HAS_ACTIVITY`, close it instead). A student who leaves a course keeps their tasks.
- **Upload parsing no longer runs Tika's built-in OCR;** scans go through the background OCR job instead.
- **Full-text search** runs over `masked_text`, so searching for an IIN or a phone number finds nothing.
- **Stable error codes:** 76 codes in `ErrorCodes`, all with Russian texts in `CODE_MESSAGES`. New audit actions have
  Russian labels in the admin log.

### Security

- Rules A–C from Sprint 2 also cover the new paths: a type set by hand to `EXAM_ANSWERS` becomes `TEACHER_ONLY` and
  is never analyzed; a document being re-recognized is hidden from students again until it is classified.
- Every new endpoint is authorized in its service through `AccessControlService` and `Permission`; there are no role
  checks in the new controller methods.
- No AI or OCR call runs in a request thread or inside a database transaction. The only exception is course Q&A,
  which calls the AI in the request thread as before. Jobs are submitted after commit.

### Database (applied automatically at startup, safe with existing data)

| Change | How existing rows are handled |
|---|---|
| `assignments.status` check constraint | rebuilt from the enum to accept `DRAFT` (`AssignmentStatusMigration`); rows keep their status |
| `assignments.ai_match_key`, `assignments.duplicate_of_id` | nullable; `duplicate_of_id` is `ON DELETE SET NULL` |
| `documents.masked_text`, `pii_types`, `pii_scan` | nullable; `DocumentPrivacyBackfill` masks every document with text at startup, 50 per batch, and again when the privacy settings change |
| `documents.pii_count`, `original_allowed_for_students`, `text_from_ocr` | NOT NULL with `DEFAULT 0` / `false` |
| `documents.ocr_status` | nullable (`null` = no OCR needed) |
| `documents.text_tsv` | dropped and recreated once over `masked_text` if it was generated from `extracted_text`; GIN index recreated |
| `document_chunks` | rebuilt from the masked text by the backfill |

### Configuration

New (all optional, defaults shown); see `application.example.yml`, `backend/.env.example` and the README:

| Property | Env variable | Default |
|---|---|---|
| `app.ai.auto-publish-assignments` | `AI_AUTO_PUBLISH_ASSIGNMENTS` | `false` |
| `app.privacy.enabled` | `PRIVACY_ENABLED` | `true` |
| `app.privacy.public-email-domains` | `PRIVACY_PUBLIC_EMAIL_DOMAINS` | — |
| `app.ocr.enabled` | `OCR_ENABLED` | `false` |
| `app.ocr.tesseract-path` | `OCR_TESSERACT_PATH` | — (PATH) |
| `app.ocr.languages` | `OCR_LANGUAGES` | `rus+kaz+eng` |
| `app.ocr.timeout` | `OCR_TIMEOUT` | `120s` |
| `app.ocr.max-pages` | `OCR_MAX_PAGES` | `20` |

### Known limitations

- **File names are not masked for students:** a file named after a person shows that name in lists and search. They
  are masked only in requests to the AI.
- A question typed into the Q&A chat is sent to the AI as written; only the document context is masked.
- Masking finds the formats it knows (IIN, KZ phones, e-mails, ID / passport numbers); names and addresses stay.
- OCR reads only the first 20 pages of a PDF; handwriting and low-quality scans may give poor text, shown as
  "Распознано со скана".
- Deadline changes are not yet sent to students (the `AssignmentEvents` hook is empty until Sprint 4).
- The PostgreSQL integration tests (`AssignmentLifecyclePostgresTest`, `-Dit.db.url=…`) have not run yet. The SQL of
  `fanOut`, task sync, search and the startup migrations is covered by checks of the query texts and by starting the
  app.

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
