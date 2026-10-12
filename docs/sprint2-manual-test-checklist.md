# Sprint 2 — manual browser test checklist

Three accounts, one course:

| Account | Role | Relation to INF345 |
|---|---|---|
| **T** | TEACHER | teacher of INF345 |
| **S** | STUDENT | enrolled in INF345 |
| **A** | ADMIN | — |

Use separate browser profiles (or one normal and one private window) so two accounts can be logged in at once.

## Prerequisites

- [ ] `docker-compose up -d`; backend started with `.\mvnw.cmd spring-boot:run` from `backend/`, frontend with
      `npm run dev`.
- [ ] `backend/.env` has a real Groq key (`AI_API_KEY`), `AI_BASE_URL=https://api.groq.com/openai/v1`,
      `AI_MODEL=openai/gpt-oss-120b`.
- [ ] Backend log at startup has no errors; it shows `AI client: enabled=true, … model openai/gpt-oss-120b …`,
      `AI model openai/gpt-oss-120b is available for this API key` and
      `Document storage at …\uploads`.
- [ ] Test files ready: a syllabus PDF with 2–3 dated labs, a lecture PDF, a DOCX practice task with a deadline, a
      PNG, an exam answer key PDF, a `.txt` renamed to `fake.pdf`, a ZIP with ~10 lecture PDFs + 1 `.exe`.

## Teacher of INF345 (T)

**Upload (US-04, US-05)**
- [ ] Open INF345. The upload panel shows "Тип документа: Определить автоматически (ИИ)", "Кто видит", course fixed.
- [ ] Drag the syllabus PDF, lecture PDF, DOCX task and PNG onto the drop zone at once.
- [ ] The response is immediate; the toast says "Загружено документов: 4. ИИ определяет тип файлов…".
- [ ] Each row goes "Загружено" → spinner "ИИ определяет тип…" → a colored badge with confidence, e.g.
      "Силлабус · 96%" with a bot icon. Nothing freezes while this happens.
- [ ] The PNG (no text) ends as some category or "Тип не определён" — not stuck on the spinner.
- [ ] For the syllabus and the DOCX task, a short "дедлайны…" spinner, then a green line
      "ИИ создал дедлайнов: N — «file»".
- [ ] "К заданиям курса" scrolls to "Задания курса"; the new assignments are there without a page reload.
- [ ] "Документы курса" shows the final badges after the AI finished (no manual refresh needed).

**Validation**
- [ ] Upload `fake.pdf` → listed under skipped with "Содержимое файла не соответствует расширению".
- [ ] Upload an empty file → skipped as "Пустой файл".

**ZIP (US-06)**
- [ ] Upload the ZIP. The panel shows "Импортировано 10 из 11 файлов — «…»" and the `.exe` as skipped.
- [ ] All 10 rows get their badges within a few minutes; the log shows no stack traces (429 retries as WARN are fine).

**Exam answers (rules A, B)**
- [ ] Choose "Ответы к экзамену" manually and "Студенты курса" as visibility; the hint says only the teacher will see
      it. Upload the answer key.
- [ ] The row shows a red badge with a lock; hovering shows "Скрыто от студентов"; the list shows
      "Только преподаватель курса".
- [ ] Upload the answer key again with "Определить автоматически (ИИ)". If the AI says exam answers, the same lock
      and visibility appear.
- [ ] Open the document → "Анализ ИИ и задачи": no analyze button; the lock note explains
      "Ответы к экзамену не анализируются на задания".

**Reclassify**
- [ ] In "Документы курса", click "Переклассифицировать" on the lecture. The button shows "ИИ определяет…", the row
      shows the spinner, then a badge again. The page does not navigate.
- [ ] Same from the document page header and from "Хранилище документов".

**Imports history**
- [ ] `GET /api/v1/courses/{INF345 id}/imports` (browser dev tools or the address bar with the token) lists the ZIP with
      10/11 and `pendingCount` 0 once done.

## Student enrolled in INF345 (S)

- [ ] While T's upload is still "ИИ определяет тип…", S's INF345 document list does **not** show those files.
- [ ] After classification, S sees the lecture, syllabus, DOCX task and PNG — with badges and confidence, but no
      "Переклассифицировать" and no delete.
- [ ] S does **not** see the exam answer keys in the course list, in "Хранилище документов", or in search.
- [ ] Opening an exam answer key by URL (`/documents/{id}`, id from T's window) shows "access denied".
- [ ] Course chat: asking about something only in the answer key gives
      "В предоставленных документах курса нет информации по данному вопросу." — no quotes from it.
- [ ] Course chat about the syllabus answers with citations "[file, фрагмент N]".
- [ ] "Мои задачи" lists the deadlines the AI extracted from the syllabus and the DOCX task; none come from exam answers.
- [ ] S has no upload panel on the course page. Uploading through the API (`POST /api/v1/documents/upload-multiple`
      with S's token) returns 403.
- [ ] `GET /api/v1/documents/{exam id}/status` with S's token returns 403.
- [ ] `GET /api/v1/courses/{INF345 id}/imports` with S's token returns 403.

## ADMIN (A)

- [ ] "Хранилище документов" shows documents of all courses, including the exam answer keys (with lock badge).
- [ ] A can upload into INF345 and sees the same status flow as T.
- [ ] A can "Переклассифицировать" any document and delete it.
- [ ] `/courses/{INF345 id}/imports` works for A.
- [ ] Admin page → audit log contains `DOCUMENT_UPLOADED` (with "AI classification pending"), `DOCUMENT_CLASSIFIED`
      (category and confidence), `BATCH_IMPORTED` (archive, counts), and `ACCESS_DENIED` for S's attempts above.
- [ ] Exam answer upload with "Студенты курса" produced a `DOCUMENT_UPLOADED` entry ending with
      "visibility forced to TEACHER_ONLY".

## Resilience (any teacher or ADMIN)

- [ ] Stop the backend while a ZIP is still being classified; start it again. The log shows
      "… back to PENDING" and "Queued N PENDING document(s)"; the documents finish.
- [ ] With `AI_ENABLED=false`: an "auto" upload still succeeds and the file shows "Тип не определён";
      "Переклассифицировать" shows an error toast; the student can see it (category OTHER).
- [ ] Dark theme: badges, notices and the upload panel are readable.
