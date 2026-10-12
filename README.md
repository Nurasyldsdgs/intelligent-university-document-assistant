# UniDocs Assistant

Course document vault with AI classification, deadline extraction and Q&A.
Backend: Spring Boot 3 (Java 21) + PostgreSQL. Frontend: React + Vite.

## Run locally

Prerequisites: Docker, JDK 21, Maven, Node.js 20+.

```bash
# 1. Database (PostgreSQL 16 on localhost:5432)
docker-compose up -d

# 2. Backend config: secrets come from backend/.env, never from committed files
cp backend/src/main/resources/application.example.yml backend/src/main/resources/application.yml
cp backend/.env.example backend/.env
#    then edit backend/.env: AI_API_KEY (Groq), MAIL_*, GOOGLE_* as needed

# 3. Backend on http://localhost:8080 (run from backend/ so backend/.env is picked up)
cd backend
mvn spring-boot:run

# 4. Frontend on http://localhost:5173 (in a second terminal)
cd frontend
npm install
npm run dev
```

Uploaded files are stored in `backend/uploads/` (set `UPLOAD_DIR` to change it); this folder is git-ignored.
Without an AI key the app still works, just without classification, deadline extraction and Q&A.
