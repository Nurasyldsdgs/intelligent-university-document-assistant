# Sprint 3 — manual browser test checklist

Three accounts, one course:

| Account | Role | Relation to INF345 |
|---|---|---|
| **T** | TEACHER | teacher of INF345 |
| **S** | STUDENT | enrolled in INF345 |
| **D** | ADMIN (dean's office) | — |

Use separate browser profiles (or one normal and one private window) so two accounts can be logged in at once.
Automated coverage of the same cases: `Sprint3QaTest` (QA-AI-008, QA-SEC-009, QA-OCR-007). This list checks what a
unit test cannot: the real Groq model, the real Tesseract, PostgreSQL and the browser.

## Prerequisites

- [ ] `docker-compose up -d`; backend started with `.\mvnw.cmd spring-boot:run` from `backend/`, frontend with
      `npm run dev`. Start against a database that **already has Sprint 2 data** at least once, to check the migrations.
- [ ] `backend/.env` has a real Groq key, `OCR_ENABLED=true` and `OCR_TESSERACT_PATH=C:\Program Files\Tesseract-OCR`
      (Tesseract installed as in the README section "OCR (распознавание сканов)").
- [ ] Backend log at startup has no errors and shows:
  - `OCR ready: Tesseract with rus+kaz+eng …`;
  - the masking backfill line (number of documents masked, `0` on the second start);
  - `Full-text search columns and indexes on documents and document_chunks are in place`.
- [ ] Test files ready:
  - `syllabus.pdf` with text: 2–3 labs with dates, plus a student line such as
    "Староста: Иванов А., ИИН 990101300013, тел. +7 701 123 45 67, ivanov@mail.ru";
  - `scan.pdf`: the same syllabus printed and scanned (or a PDF of page images, no text layer);
  - `lab1.docx`: "Лабораторная работа №1" with the same deadline as lab 1 of the syllabus;
  - a multi-page scan for the OCR failure check.

## Teacher of INF345 (T)

**AI drafts (US-08)**
- [ ] Upload `syllabus.pdf` with "Определить автоматически (ИИ)". The upload notice reports drafts for review, not
      published deadlines.
- [ ] The course page shows "Черновики от ИИ (N)" with the labs, their deadlines and requirements. "Задания курса" does
      not list them yet.
- [ ] "Изменить" on a draft: change the title and the deadline, save. "Черновик сохранён"; the draft shows the new values.
- [ ] Clear the deadline of a draft and click "Опубликовать": a confirmation warns "Нет срока сдачи…".
- [ ] "Удалить черновик" asks for confirmation and removes it.
- [ ] "Опубликовать все": the confirmation says "Их получат все студенты курса."; afterwards the drafts move to
      "Задания курса" and the draft block disappears.

**Editing a published assignment**
- [ ] After S has set lab 1 to "В работе" (see below), "Изменить" lab 1 and move its deadline by a week. The toast says
      the students see the change.
- [ ] D's audit log shows the deadline change with the old and the new date.
- [ ] Ask the AI to analyze the syllabus again (document page, "Анализ ИИ и задачи"). Lab 1 keeps the teacher's title
      and deadline; no second "Лабораторная работа 1" appears.
- [ ] "Закрыть задание" on lab 2: it stays in the list as "Закрыто". "Открыть снова" makes it active again.
- [ ] Delete a published assignment S has worked on: it is refused with "close it instead". Close it instead.

**Duplicates**
- [ ] Upload `lab1.docx` (automatic type). Its draft shows "Похоже на: Лабораторная работа 1 …" with
      "Это дубль — удалить черновик" and "Это другое задание".
- [ ] "Это другое задание" removes the mark ("Отметка «похоже на» снята"). Publishing a still-marked draft asks
      "Опубликовать возможный дубль?".

**Personal data (US-09)**
- [ ] `syllabus.pdf` shows the badge "Личные данные: 3"; hovering lists the kinds.
- [ ] Tab "Текст" shows the **original** text, with the IIN, phone and e-mail.
- [ ] Opening the "Просмотр" tab and downloading the file both appear in D's audit log as openings of an original with
      personal data.
- [ ] "Разрешить студентам оригинал" asks for confirmation; switch it on, check as S (below), then switch it off.

**OCR (US-07)**
- [ ] Upload `scan.pdf` (automatic type). The row and the course list show "Распознаём скан…", then the category
      appears without a reload, with "Распознано со скана". For a syllabus, drafts appear in "Черновики от ИИ".
- [ ] Tab "Текст" shows the recognized text (Russian and Kazakh letters readable).
- [ ] Set `OCR_TIMEOUT=1s`, restart, upload the multi-page scan. The document stays in the course with
      "Нужна проверка"; its page shows the panel "Нужна проверка: текст скана распознать не удалось."
- [ ] "Указать тип вручную" → "Силлабус" → "Сохранить": the badge changes and the panel's type follows; D's log shows
      "Тип документа указан вручную".
- [ ] Remove `OCR_TIMEOUT`, restart, open another failed scan, "Повторить распознавание": the panel disappears,
      "Распознаём скан…" runs, then the result appears. D's log shows "Повторное распознавание скана".

## Student of INF345 (S)

- [ ] While T's uploads are being classified or recognized ("Распознаём скан…" for T), S does not see them in the
      course list, in "Хранилище документов" or in search.
- [ ] Before T publishes: S sees no drafts and has no new tasks in "Мои задачи". After "Опубликовать все": each lab is
      in "Мои задачи".
- [ ] Set lab 1 to "В работе" (and hand in a file if available). After T moves the deadline, the task shows the new
      deadline and is still "В работе" with the file.
- [ ] Open `syllabus.pdf`: tab "Текст" shows "[ИИН скрыт]", "[телефон скрыт]", "[e-mail скрыт]" and
      "Часть личных данных скрыта."; T's e-mail is readable.
- [ ] "Просмотр" and the download button show "Оригинал доступен только преподавателю" instead of the file. Calling
      `GET /api/v1/documents/{id}/download` directly gives 403 with the restricted-original message.
- [ ] After T allowed the original: the file opens and downloads; the "Текст" tab is still masked. After T switched it
      off: 403 again.
- [ ] Search for `990101300013` and for `701 123 45 67`: no results. Search for "Алгоритмы": the syllabus is found and
      its snippet has no personal data.
- [ ] Chat with AI on the syllabus: "Какой ИИН у старосты?" → the answer says the data is hidden, never a number.
      Course chat: same question, same result.
- [ ] As S, `POST /api/v1/documents/{id}/ocr/retry`, `POST /api/v1/documents/{id}/category`,
      `POST /api/v1/assignments/{id}/publish` and `PATCH /api/v1/assignments/{id}` all answer 403.

## Dean's office (D, ADMIN)

- [ ] D sees the drafts of INF345 and can publish, edit, close and reopen like T.
- [ ] D's "Текст" tab shows the original; opening or downloading the original is audited under D's own e-mail.
- [ ] D can use "Указать тип вручную" and "Повторить распознавание" on a failed scan.
- [ ] The audit log on the admin page shows Russian labels, with no raw codes, for: publishing, editing a deadline, closing,
      reopening, opening an original with personal data, allowing / denying the original, setting a type by hand,
      retrying recognition, and refused access.
- [ ] Turn `PRIVACY_PUBLIC_EMAIL_DOMAINS` to your university's domain, restart: the backfill re-masks the documents
      once (log), and e-mails on that domain become readable for S.

## Upgrade of an existing database

- [ ] After the first start on Sprint 2 data: old assignments keep their status (`ACTIVE` / `CLOSED`), old documents
      have masked text and the "Текст" tab works for S, search still finds old documents by ordinary words.
- [ ] A second start logs nothing to backfill and no schema rebuild.
