# SkillForge manual startup guide

This guide explains how to start the backend and frontend locally and how to configure the required environment variables.

## 1) Prerequisites

Install:
- Java 21
- Node.js 20+ (recommended 22 LTS)
- Docker Desktop or Docker Engine
- Git

## 2) Start PostgreSQL

From the project root:

```powershell
cd C:\Personal\SkillForge
docker compose up -d postgres
```

This starts PostgreSQL on:
- Host: `localhost`
- Port: `5432`
- Database: `skillforge`
- Username: `skillforge`
- Password: `skillforge`

## 3) Backend startup

Open a PowerShell terminal and run:

```powershell
cd C:\Personal\SkillForge\skillforge

$env:JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Kolkata"
$env:DB_URL="jdbc:postgresql://localhost:5432/skillforge"
$env:DB_USERNAME="skillforge"
$env:DB_PASSWORD="skillforge"
$env:JWT_SECRET="dev-secret-dev-secret-dev-secret-123456"
$env:CONTENT_ROOT="C:\Personal\SkillForge\content"
$env:ADMIN_EMAILS="admin@example.com"
$env:FRONTEND_URL="http://localhost:3000"

.\mvnw.cmd spring-boot:run
```

Expected result:
- Backend is available at `http://localhost:8080`
- Swagger UI is available at `http://localhost:8080/swagger-ui.html`

## 4) Frontend startup

Open a second PowerShell terminal and run:

```powershell
cd C:\Personal\SkillForge\frontend

$env:BACKEND_URL="http://localhost:8080"
$env:COOKIE_SECURE="false"

npm install
npm run dev
```

Expected result:
- Frontend is available at `http://localhost:3000`
- Login page: `http://localhost:3000/login`

## 5) Full Docker stack (optional)

From the project root:

```powershell
cd C:\Personal\SkillForge

$env:JWT_SECRET="dev-secret-dev-secret-dev-secret-123456"
$env:ADMIN_EMAILS="admin@example.com"
$env:FRONTEND_URL="http://localhost:3000"
$env:COOKIE_SECURE="false"

docker compose --profile app up -d --build
```

Then use:
- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`

To stop it:

```powershell
docker compose --profile app down
```

## 6) Example .env file

If you want to store these variables in a file, use values similar to the following:

```env
DB_URL=jdbc:postgresql://localhost:5432/skillforge
DB_USERNAME=skillforge
DB_PASSWORD=skillforge
JWT_SECRET=dev-secret-dev-secret-dev-secret-123456
CONTENT_ROOT=C:\Personal\SkillForge\content
ADMIN_EMAILS=admin@example.com
FRONTEND_URL=http://localhost:3000
BACKEND_URL=http://localhost:8080
COOKIE_SECURE=false
MAIL_ENABLED=false
MAIL_HOST=
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=no-reply@skillforge.local
```

Notes:
- `JWT_SECRET` must be at least 32 characters long.
- `CONTENT_ROOT` must point to the `content/` folder in this project.
- `COOKIE_SECURE` should be `true` only when serving over HTTPS.
- If `MAIL_ENABLED=false`, reset links are logged to the backend console instead of being emailed.

## 7) Default admin user

The app seeds a default admin account:
- Email: `admin@example.com`
- Password: `password123`

## 8) Troubleshooting

If startup fails:
- Make sure PostgreSQL is running.
- Check that `JWT_SECRET` is set and not empty.
- Confirm `CONTENT_ROOT` points to the real `content` folder.
- Validate the Java and Node versions.
- Restart the backend after changing any env variables.

## 9) Quick smoke test

After both services are running:

1. Open `http://localhost:3000/login`
2. Sign in using `admin@example.com / password123`
3. Open `http://localhost:8080/swagger-ui.html` to inspect the API
4. Try the login endpoint or a few protected endpoints

## 10) Commands to check local host availability

1. Check the connection -
Get-NetTCPConnection -LocalPort 8080 -State Listen |
  Select-Object LocalAddress, LocalPort, OwningProcess


2. Use owning process value to identify the process:
  ```Get-Process -Id <PID>```

3. Stop the process:
```Stop-Process -Id <PID> -Force```
or 
```Stop-Process -Id <PID>```

If Dokcer owns the port then --
docker ps --filter "publish=8080"
docker stop <container-id>