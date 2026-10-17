# UniDocs Assistant

Course document vault with AI classification, deadline extraction and Q&A.
Backend: Spring Boot 3 (Java 21) + PostgreSQL. Frontend: React + Vite.

## Run locally

Prerequisites: Docker, JDK 21, Node.js 20+. Maven does not need to be installed: the Maven Wrapper
(`backend/mvnw`, `backend/mvnw.cmd`) downloads Maven 3.9.9 into `~/.m2/wrapper` on first run.

```bash
# 1. Database (PostgreSQL 16 on localhost:5432)
docker-compose up -d

# 2. Backend config: secrets come from backend/.env, never from committed files
cp backend/src/main/resources/application.example.yml backend/src/main/resources/application.yml
cp backend/.env.example backend/.env
#    then edit backend/.env: AI_API_KEY (Groq), MAIL_*, GOOGLE_* as needed

# 3. Backend on http://localhost:8080 (run from backend/ so backend/.env is picked up)
cd backend
./mvnw spring-boot:run          # macOS / Linux / Git Bash
.\mvnw.cmd spring-boot:run      # Windows PowerShell or cmd

# 4. Frontend on http://localhost:5173 (in a second terminal)
cd frontend
npm install
npm run dev
```

Uploaded files are stored in `backend/uploads/` (set `UPLOAD_DIR` to change it); this folder is git-ignored.
Without an AI key the app still works, just without classification, deadline extraction and Q&A.

## OCR (распознавание сканов)

Сканы PDF и изображения без текстового слоя распознаются Tesseract в фоне после загрузки (не в запросе загрузки).
По умолчанию OCR выключен; без него сканы просто остаются без текста.

Установка на Windows:

1. Скачайте установщик сборки UB Mannheim: <https://github.com/UB-Mannheim/tesseract/wiki>
   (`tesseract-ocr-w64-setup-5.x.x.exe`).
2. В установщике на шаге «Choose Components» раскройте **Additional language data (download)** и отметьте
   **Russian** и **Kazakh** (английский ставится всегда). Путь оставьте `C:\Program Files\Tesseract-OCR`.
3. Если языки не были выбраны, скачайте `rus.traineddata` и `kaz.traineddata` из
   <https://github.com/tesseract-ocr/tessdata> в папку `C:\Program Files\Tesseract-OCR\tessdata`.
4. Проверьте в PowerShell — в списке должны быть `eng`, `kaz`, `rus`:

   ```powershell
   & "C:\Program Files\Tesseract-OCR\tesseract.exe" --list-langs
   ```

5. Добавьте в `backend/.env` и перезапустите backend:

   ```dotenv
   OCR_ENABLED=true
   OCR_TESSERACT_PATH=C:\Program Files\Tesseract-OCR
   ```

При запуске в логе появится `OCR ready: Tesseract with rus+kaz+eng …`. Если Tesseract или язык не найден — одно
предупреждение `OCR is enabled but cannot run … Missing: …`, и приложение работает без OCR.

Необязательные настройки (`backend/.env.example`): `OCR_LANGUAGES` (по умолчанию `rus+kaz+eng`), `OCR_TIMEOUT`
(`120s` на документ), `OCR_MAX_PAGES` (распознаются первые 20 страниц). Проверка на настоящем Tesseract:
`.\mvnw.cmd test -Dtest=TikaOcrEngineSmokeTest -Docr.tesseract.path=C:/PROGRA~1/Tesseract-OCR` (короткий путь без
пробелов — Maven Wrapper не принимает путь с пробелом в кавычках).
