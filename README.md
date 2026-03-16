# NeuraScan Backend — Spring Boot

Complete Spring Boot REST API for the NeuraScan AI Learning Disorder Detection System.

## Prerequisites
- Java 17+
- Maven 3.8+
- MySQL 8.0 running on localhost:3306

## Quick Start

### 1. Create the database
mysql -u root -p
CREATE DATABASE ai_learning_detection;
exit;

### 2. Configure credentials
Edit: src/main/resources/application.properties
Change spring.datasource.username and spring.datasource.password to match your MySQL.

### 3. Run
mvn spring-boot:run
Backend starts on http://localhost:8080

## All API Endpoints

### Auth (public)
POST /api/auth/teacher/register
POST /api/auth/teacher/login
POST /api/auth/parent/register
POST /api/auth/parent/login
POST /api/auth/forgot-password
POST /api/auth/reset-password
GET  /api/auth/verify-reset-token?token=...

### Profile (JWT required)
PUT /api/auth/profile
PUT /api/auth/change-password

### Students (ROLE_TEACHER)
GET    /api/students
GET    /api/students/{id}
POST   /api/students
PUT    /api/students/{id}
DELETE /api/students/{id}

### Analysis (ROLE_TEACHER)
POST /api/analysis/upload
GET  /api/analysis/reports
GET  /api/analysis/dashboard

### Analysis (ROLE_PARENT)
GET /api/analysis/student-report/{studentId}
GET /api/analysis/progress/{studentId}

## Email Setup (optional - for Forgot Password)
1. Enable 2FA on your Gmail account
2. Visit https://myaccount.google.com/apppasswords
3. Generate an App Password
4. Update application.properties:
   spring.mail.username=your@gmail.com
   spring.mail.password=xxxx-xxxx-xxxx-xxxx
   app.frontend.url=http://localhost:3000

## Python AI Service
The backend expects a Python service at http://localhost:5000/analyze
POST multipart/form-data with field "file"
Response: { "dyslexia_score": 65.0, "dysgraphia_score": 45.0, "analysis": "..." }
If not running, the backend gracefully returns mock 0.0 scores.
