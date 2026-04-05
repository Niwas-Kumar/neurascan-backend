<div align="center">

# 🧠 NeuraScan Backend

**AI-Powered Learning Disorder Detection Platform**

*Helping educators and parents identify dyslexia & dysgraphia early — built for scale.*

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.0-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Firebase](https://img.shields.io/badge/Firebase-Firestore-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)](https://firebase.google.com/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

[Live API](https://neurascan-python-ai.onrender.com) · [Frontend](https://neurascan-frontend-blond.vercel.app) · [Report a Bug](../../issues) · [Request Feature](../../issues)

</div>

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Local Setup](#local-setup)
  - [Docker Setup](#docker-setup)
- [Environment Variables](#-environment-variables)
- [API Reference](#-api-reference)
- [Feature Flags](#-feature-flags)
- [Deployment](#-deployment)
- [Contributing](#-contributing)

---

## 🌟 Overview

**NeuraScan** is a SaaS platform that assists teachers and parents in the early detection of learning disorders — specifically **dyslexia** and **dysgraphia** — in school-age children.

The backend is a production-grade **Spring Boot 3** REST API that:

- Authenticates teachers and parents with **JWT** and **Google OAuth** (Firebase)
- Manages students, classrooms, and parent–student relationships
- Accepts handwriting samples and routes them to a **Python AI microservice** for scoring
- Delivers analysis reports and progress-tracking dashboards
- Distributes adaptive **quizzes** to students via shareable links
- Sends transactional emails (OTP, password reset) via **SendGrid**

---

## ✨ Features

| Feature | Role |
|---|---|
| Teacher & Parent registration / login | Both |
| Google Sign-In via Firebase | Both |
| OTP email verification (pre-registration) | Both |
| Password reset via email link | Both |
| Classroom management (create, update, archive) | Teacher |
| Student management (CRUD) | Teacher |
| AI handwriting analysis upload | Teacher |
| Analysis reports & dashboard | Teacher |
| Quiz creation & distribution (shareable link) | Teacher |
| Student-report & progress view | Parent |
| Parent–Student relationship verification | Both |
| Health & readiness endpoints | Ops |
| Feature flags (env-driven) | Ops |

---

## 🏗 Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                         Clients                                  │
│          React Frontend (Vercel)  ·  Mobile App                  │
└───────────────────────────┬──────────────────────────────────────┘
                            │ HTTPS / REST + JWT
┌───────────────────────────▼──────────────────────────────────────┐
│                   NeuraScan Spring Boot API                      │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Controllers (REST Layer)                                   │ │
│  │  Auth · Students · Classes · Analysis · Quiz · Health       │ │
│  └──────────────────────┬──────────────────────────────────────┘ │
│  ┌───────────────────────▼──────────────────────────────────────┐ │
│  │  Services (Business Logic Layer)                            │ │
│  │  AuthService · AnalysisService · QuizService · EmailService │ │
│  │  ClassService · StudentService · OTPService · FileStorage   │ │
│  └──────────────────────┬──────────────────────────────────────┘ │
│  ┌───────────────────────▼──────────────────────────────────────┐ │
│  │  Security Layer                                             │ │
│  │  JwtAuthFilter · Spring Security · Firebase Token Verifier  │ │
│  └──────────────────────┬──────────────────────────────────────┘ │
└──────────────────────────┼───────────────────────────────────────┘
                           │
          ┌────────────────┼────────────────────┐
          │                │                    │
┌─────────▼──────┐ ┌───────▼────────┐ ┌────────▼────────┐
│   Firestore    │ │  Python AI     │ │   SendGrid      │
│  (Database)    │ │  Microservice  │ │  (Email)        │
│                │ │  /analyze      │ │                 │
│  Collections:  │ │                │ │  OTP · Reset    │
│  teachers      │ │  Dyslexia &    │ │  Password       │
│  parents       │ │  Dysgraphia    │ └─────────────────┘
│  students      │ │  scoring       │
│  classrooms    │ └────────────────┘
│  quizzes       │
│  analysis_rpts │
└────────────────┘
```

### Request Lifecycle

```
Client Request
    │
    ▼
JwtAuthenticationFilter  ──── invalid token ───▶  401 Unauthorized
    │ valid token
    ▼
Spring Security (@PreAuthorize)  ── wrong role ──▶  403 Forbidden
    │ authorized
    ▼
Controller  ──▶  Service  ──▶  Firestore / AI Service / Email
    │
    ▼
ApiResponse<T>  ──▶  Client
```

---

## 🛠 Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Java 17 |
| **Framework** | Spring Boot 3.2 |
| **Security** | Spring Security 6, JWT (JJWT 0.11) |
| **Database** | Google Cloud Firestore (Firebase Admin SDK 9.2) |
| **Auth Provider** | Firebase Authentication (Google OAuth) |
| **Email** | SendGrid API |
| **File Storage** | Local filesystem (configurable path) |
| **AI Integration** | HTTP client → Python FastAPI microservice |
| **Build** | Maven 3.8+ |
| **Containerisation** | Docker (multi-stage, eclipse-temurin:17-jre) |
| **Hosting** | Render (backend), Vercel (frontend), HF Spaces (AI) |

---

## 📂 Project Structure

```
neurascan-backend/
├── src/
│   └── main/
│       ├── java/com/ai/learningdetection/
│       │   ├── config/          # Spring beans: Security, Firebase, Mail, FileStorage
│       │   ├── controller/      # REST endpoints
│       │   │   ├── AuthController.java
│       │   │   ├── AnalysisController.java
│       │   │   ├── ClassController.java
│       │   │   ├── StudentController.java
│       │   │   ├── QuizController.java
│       │   │   ├── PublicQuizController.java
│       │   │   ├── ParentStudentController.java
│       │   │   ├── ProfileController.java
│       │   │   ├── PasswordResetController.java
│       │   │   └── HealthController.java
│       │   ├── dto/             # Request / Response objects
│       │   ├── entity/          # Firestore document models
│       │   ├── exception/       # Custom exceptions + GlobalExceptionHandler
│       │   ├── security/        # JWT filter, UserDetails, EntryPoint
│       │   ├── service/         # Business logic
│       │   └── util/            # JwtUtil, RiskLevelUtil
│       └── resources/
│           └── application.properties
├── Dockerfile
├── firebase.json
├── firestore.indexes.json
├── pom.xml
├── CHANGELOG.md
└── DEPLOYMENT_NOTES.md
```

---

## 🚀 Getting Started

### Prerequisites

| Tool | Version |
|---|---|
| Java JDK | 17+ |
| Maven | 3.8+ |
| Firebase project | with Firestore enabled |
| `serviceAccountKey.json` | downloaded from Firebase console |

> **No MySQL required.** NeuraScan uses **Google Cloud Firestore** as its primary database.

---

### Local Setup

#### 1. Clone the repository

```bash
git clone https://github.com/Niwas-Kumar/neurascan-backend.git
cd neurascan-backend
```

#### 2. Add your Firebase service account key

Download `serviceAccountKey.json` from your Firebase project:

> Firebase Console → Project Settings → Service Accounts → Generate new private key

Place it in the project root:

```
neurascan-backend/
└── serviceAccountKey.json   ← here
```

#### 3. Configure environment variables

Copy the template and fill in your values:

```bash
cp .env.example .env   # or export variables directly
```

See [Environment Variables](#-environment-variables) for a full reference.

#### 4. Build and run

```bash
mvn spring-boot:run
```

The API starts at **http://localhost:8080**

Verify it's running:

```bash
curl http://localhost:8080/ping
# → {"status":"UP"}
```

---

### Docker Setup

#### Build the image

```bash
docker build -t neurascan-backend:latest .
```

#### Run the container

```bash
docker run -p 8080:8080 \
  -e JWT_SECRET=your_super_secret_key \
  -e AI_SERVICE_URL=https://your-ai-service.com/analyze \
  -e SENDGRID_API_KEY=SG.xxxxxxxxxxxx \
  -e SENDGRID_FROM_EMAIL=noreply@yourdomain.com \
  -e GOOGLE_CLIENT_ID=your_google_client_id \
  -e APP_FRONTEND_URL=http://localhost:3000 \
  -v $(pwd)/serviceAccountKey.json:/app/serviceAccountKey.json:ro \
  neurascan-backend:latest
```

---

## 🔧 Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `JWT_SECRET` | ✅ | (insecure default) | Secret key for signing JWTs — **must be overridden in production** |
| `AI_SERVICE_URL` | ✅ | Render URL | Full URL to the Python AI `/analyze` endpoint |
| `AI_SERVICE_TIMEOUT_MS` | ❌ | `10000` | Timeout in ms for AI service calls |
| `SENDGRID_API_KEY` | ✅ | _(empty)_ | SendGrid API key (`SG.xxx…`) |
| `SENDGRID_FROM_EMAIL` | ✅ | _(empty)_ | Verified sender email address |
| `GOOGLE_CLIENT_ID` | ✅ | (example ID) | Google OAuth client ID for Firebase token verification |
| `APP_FRONTEND_URL` | ✅ | Vercel URL | Used in password-reset email links |
| `CORS_ALLOWED_ORIGINS` | ❌ | Vercel URL | Comma-separated list of allowed CORS origins |
| `PORT` | ❌ | `8080` | Server port (set automatically on Render/Heroku) |
| `FEATURE_CONSENT_ENABLED` | ❌ | `false` | Enable consent modal enforcement |
| `FEATURE_RISK_EXPLANATION_ENABLED` | ❌ | `false` | Show confidence + explanation data |
| `FEATURE_TEACHER_RECOMMENDATIONS_ENABLED` | ❌ | `false` | Teacher recommendation engine |
| `FEATURE_EXPORT_AUDITLOG_ENABLED` | ❌ | `false` | Audit log export |
| `FEATURE_OFFLINE_MODE_ENABLED` | ❌ | `false` | Offline mode support |
| `FEATURE_ADMIN_PANEL_ENABLED` | ❌ | `false` | Admin panel access |

> ⚠️ **Never commit `serviceAccountKey.json` or secrets to version control.**

---

## 📡 API Reference

All endpoints return a unified envelope:

```json
{
  "success": true,
  "message": "Human-readable message",
  "data": { }
}
```

### Authentication

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/teacher/register` | Public | Register a teacher |
| `POST` | `/api/auth/teacher/login` | Public | Teacher login → JWT |
| `POST` | `/api/auth/parent/register` | Public | Register a parent |
| `POST` | `/api/auth/parent/login` | Public | Parent login → JWT |
| `POST` | `/api/auth/firebase-login` | Public | Google Sign-In via Firebase ID token |
| `POST` | `/api/auth/send-otp` | Public | Send OTP to email |
| `POST` | `/api/auth/verify-otp` | Public | Verify OTP |
| `POST` | `/api/auth/parent/link-child` | Parent | Link parent account to student |

### Password Reset

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/password-reset/request` | Public | Send password-reset email |
| `POST` | `/api/password-reset/reset` | Public | Reset password with token |
| `GET` | `/api/password-reset/verify?token=…` | Public | Validate reset token |

### Profile

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `PUT` | `/api/profile` | JWT | Update profile details |
| `PUT` | `/api/profile/change-password` | JWT | Change password |

### Students `ROLE_TEACHER`

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/students` | List all students for the teacher |
| `GET` | `/api/students/{id}` | Get student by ID |
| `POST` | `/api/students` | Create a new student |
| `PUT` | `/api/students/{id}` | Update student |
| `DELETE` | `/api/students/{id}` | Archive student |

### Classrooms `ROLE_TEACHER`

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/classes` | List teacher's classrooms |
| `GET` | `/api/classes/{id}` | Get classroom details |
| `POST` | `/api/classes` | Create a classroom |
| `PUT` | `/api/classes/{id}` | Update classroom |
| `DELETE` | `/api/classes/{id}` | Archive classroom |

### Analysis

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/analysis/upload` | Teacher | Upload handwriting image → AI analysis |
| `GET` | `/api/analysis/reports` | Teacher | All reports for teacher's students |
| `GET` | `/api/analysis/dashboard` | Teacher | Dashboard statistics |
| `GET` | `/api/analysis/student-report/{studentId}` | Parent | Latest report for child |
| `GET` | `/api/analysis/progress/{studentId}` | Parent | Progress history for child |

**Upload request** (`multipart/form-data`):

```
studentId  (string)
file       (image file, max 20 MB)
```

**AI response shape** (from Python microservice):

```json
{
  "dyslexia_score": 65.0,
  "dysgraphia_score": 45.0,
  "analysis": "Detailed narrative …"
}
```

### Quizzes

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/quizzes` | Teacher | Create a quiz |
| `GET` | `/api/quizzes` | Teacher | List teacher's quizzes |
| `GET` | `/api/quizzes/{quizId}` | Teacher | Get quiz details |
| `POST` | `/api/quizzes/{quizId}/submit` | JWT | Submit quiz responses |
| `GET` | `/api/quizzes/{quizId}/responses` | Teacher | View all responses |
| `GET` | `/api/quizzes/public/{linkToken}` | Public | Load quiz via shareable link |

### Parent–Student Relationships

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/parent-student/request` | Parent | Request relationship with student |
| `GET` | `/api/parent-student/my-students` | Parent | List verified children |
| `PUT` | `/api/parent-student/{id}/verify` | Teacher | Approve relationship |

### Health & Ops

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/ping` | Public | Keep-alive / wake ping |
| `GET` | `/health` | Public | Lightweight UP check |
| `GET` | `/api/v1/health` | Public | Full health: Firestore + AI + feature flags |

---

## 🚩 Feature Flags

All features default to **off** and are toggled via environment variables:

| Flag | Env Variable |
|---|---|
| Consent modal | `FEATURE_CONSENT_ENABLED` |
| Risk explanation | `FEATURE_RISK_EXPLANATION_ENABLED` |
| Teacher recommendations | `FEATURE_TEACHER_RECOMMENDATIONS_ENABLED` |
| Audit log export | `FEATURE_EXPORT_AUDITLOG_ENABLED` |
| Offline mode | `FEATURE_OFFLINE_MODE_ENABLED` |
| Admin panel | `FEATURE_ADMIN_PANEL_ENABLED` |

---

## 🌐 Deployment

### Render (recommended)

1. Connect this GitHub repository to a new **Web Service** on [Render](https://render.com).
2. Set **Build Command**: `mvn clean package -DskipTests`
3. Set **Start Command**: `java -jar target/learning-detection-2.0.0.jar`
4. Add all [environment variables](#-environment-variables) in the Render dashboard.
5. Add a secret file `/app/serviceAccountKey.json` using the **Secret Files** feature.

### Health check

Configure your platform to hit:

```
GET /ping   →  HTTP 200  {"status":"UP"}
```

For detailed subsystem status (Firestore + AI service):

```
GET /api/v1/health
```

### Docker Compose (local stack)

```yaml
version: "3.9"
services:
  api:
    build: .
    ports:
      - "8080:8080"
    environment:
      - JWT_SECRET=change_me_in_production
      - AI_SERVICE_URL=http://ai:5000/analyze
      - SENDGRID_API_KEY=${SENDGRID_API_KEY}
      - SENDGRID_FROM_EMAIL=${SENDGRID_FROM_EMAIL}
      - GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
      - APP_FRONTEND_URL=http://localhost:3000
    volumes:
      - ./serviceAccountKey.json:/app/serviceAccountKey.json:ro
```

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. **Fork** the repository.
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes and ensure tests pass: `mvn test`
4. Commit with a descriptive message: `git commit -m "feat: add my feature"`
5. Push and open a **Pull Request** against `main`.

### Code style

- Follow standard Java conventions.
- Use Lombok to reduce boilerplate (`@Data`, `@Builder`, `@RequiredArgsConstructor`).
- All REST responses must use the `ApiResponse<T>` wrapper.
- Business logic belongs in the **service** layer, not controllers.

---

<div align="center">

Made with ❤️ by the NeuraScan Team

</div>
