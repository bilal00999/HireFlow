# HireFlow — Complete Setup & Testing Guide

A beginner-friendly guide to setting up and running the HireFlow AI-Powered HR Platform locally.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [System Requirements](#system-requirements)
3. [Project Setup Instructions](#project-setup-instructions)
4. [Backend Setup (Spring Boot)](#backend-setup-spring-boot)
5. [Database Setup (Neon PostgreSQL)](#database-setup-neon-postgresql)
6. [API Testing Guide](#api-testing-guide)
7. [Spring Security Explanation](#spring-security-explanation)
8. [Common Errors & Fixes](#common-errors--fixes)
9. [Development Workflow](#development-workflow)
10. [Troubleshooting Checklist](#troubleshooting-checklist)

---

## Project Overview

**HireFlow** is an AI-Powered HR Platform with:

- **Frontend**: React 18 + TypeScript + Tailwind CSS
- **Backend**: Java 21 + Spring Boot 3 + Maven
- **Database**: PostgreSQL (Neon Cloud)
- **Security**: Spring Security + JWT (future)
- **Architecture**: REST API with modern stack

### Project Structure

```
HireFlow/
├── Backend/
│   └── demo/                          # Spring Boot application
│       ├── mvnw                       # Maven wrapper (Linux/Mac)
│       ├── mvnw.cmd                   # Maven wrapper (Windows)
│       ├── pom.xml                    # Maven configuration
│       └── src/
│           ├── main/
│           │   ├── java/              # Java source code
│           │   │   └── com/example/demo/
│           │   │       ├── DemoApplication.java
│           │   │       └── config/    # Configuration classes
│           │   └── resources/
│           │       ├── application.properties
│           │       ├── static/        # Static files (CSS, JS)
│           │       └── templates/     # Thymeleaf templates
│           └── test/
│               └── java/              # Test classes
├── frontend/                          # React application
│   ├── package.json
│   ├── vite.config.ts                 # Vite bundler config
│   ├── tsconfig.json                  # TypeScript config
│   ├── src/
│   │   ├── main.tsx                   # Entry point
│   │   ├── App.tsx                    # Main component
│   │   └── assets/                    # Images, icons
│   └── public/                        # Public assets
├── SETUP_GUIDE.md                     # This file
├── package.json                       # Root package config
└── README.md                          # Project overview

```

---

## System Requirements

Before you start, ensure your system has:

### ✅ Required Software

| Tool       | Version | Download Link                                       |
| ---------- | ------- | --------------------------------------------------- |
| Git        | Latest  | https://git-scm.com/download/win                    |
| Node.js    | 18+     | https://nodejs.org/                                 |
| Java (JDK) | 21 LTS  | https://www.oracle.com/java/technologies/downloads/ |
| VS Code    | Latest  | https://code.visualstudio.com/                      |
| PostgreSQL | 14+     | https://www.postgresql.org/download/ (OR use Neon)  |

### ✅ Verify Installation

Open PowerShell and run:

```powershell
# Check Node.js
node --version
npm --version

# Check Java
java -version
javac -version

# Check Git
git --version
```

All commands should return version numbers. If not, reinstall the missing tool.

---

## Project Setup Instructions

### Step 1: Clone the Repository

```powershell
# Navigate to your projects folder
cd C:\Users\YourUsername\projects

# Clone the repository
git clone <repository-url>
cd HireFlow
```

### Step 2: Open in VS Code

```powershell
# Open VS Code in the project directory
code .
```

### Step 3: Install VS Code Extensions (Optional but Recommended)

Open VS Code Extensions (Ctrl+Shift+X) and install:

- **Extension Pack for Java** (Microsoft)
- **Spring Boot Extension Pack** (VMware)
- **Prettier - Code formatter** (Esbenp)
- **Thunder Client** or **REST Client** (for API testing)
- **Tailwind CSS IntelliSense** (Bradleys Studio)

### Step 4: Understand the Folder Structure

Each part of the project is separate:

- **Backend** (`Backend/demo/`): Spring Boot application
- **Frontend** (`frontend/`): React application

⚠️ **Important**: You'll need TWO terminals — one for backend, one for frontend.

---

## Backend Setup (Spring Boot)

### What is mvnw?

`mvnw` (Maven Wrapper) is a script that downloads and runs Maven automatically. You **don't need Maven installed globally** — mvnw handles it for you.

- **Windows**: Use `mvnw.cmd`
- **Linux/Mac**: Use `./mvnw`

### Step 1: Navigate to Backend

```powershell
cd Backend/demo
```

### Step 2: Build the Project

Maven will download all dependencies (this takes 2-3 minutes on first run):

```powershell
# Windows
mvnw.cmd clean install

# Linux/Mac
./mvnw clean install
```

**What this does**:

- `clean`: Removes old build files
- `install`: Downloads dependencies and builds the project

### Step 3: Run the Backend

```powershell
# Windows
mvnw.cmd spring-boot:run

# Linux/Mac
./mvnw spring-boot:run
```

**Expected output**:

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_|\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::        (v3.x.x)

Started DemoApplication in X.XXX seconds (process running for X.XXX)
```

The backend is now running on **http://localhost:8080**

### Step 4: Test if Backend is Running

Open your browser and visit:

```
http://localhost:8080
```

You should see a **login page** (this is Spring Security, explained below).

---

## Database Setup (Neon PostgreSQL)

### What is Neon?

Neon is a cloud PostgreSQL service. You don't need to install PostgreSQL locally.

### Step 1: Create a Neon Account

1. Go to https://neon.tech/
2. Click "Sign Up"
3. Create account with email or GitHub
4. Verify email

### Step 2: Create a New Project

1. Dashboard → "Create Project"
2. Fill in:
   - **Project name**: `hireflow` (or your choice)
   - **Database**: `neondb` (default)
   - **Region**: Choose closest to you
3. Click "Create Project"

### Step 3: Get Connection String

1. In Neon dashboard, click your project
2. Go to "Connection" tab
3. Copy the connection string under "Connection string"
4. It looks like: `postgresql://user:password@host/dbname`

### Step 4: Convert to Spring Boot Format

Neon gives you a PostgreSQL URL. You need to convert it to JDBC format.

**Neon Format**:

```
postgresql://username:password@host/dbname?sslmode=require
```

**Spring Boot JDBC Format**:

```
jdbc:postgresql://host/dbname?sslmode=require
```

### Step 5: Update application.properties

Open `Backend/demo/src/main/resources/application.properties`:

```properties
# Database Configuration (Neon PostgreSQL)
spring.datasource.url=jdbc:postgresql://YOUR_HOST/YOUR_DATABASE?sslmode=require
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA/Hibernate Configuration
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQL10Dialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Server Configuration
server.port=8080
server.servlet.context-path=/
```

### Step 6: Example Connection String

If your Neon connection string is:

```
postgresql://alex:mypassword123@ep-soft-lion-a5k1x2y3.us-east-1.neon.tech/hireflow?sslmode=require
```

Then your `application.properties` should be:

```properties
spring.datasource.url=jdbc:postgresql://ep-soft-lion-a5k1x2y3.us-east-1.neon.tech/hireflow?sslmode=require
spring.datasource.username=alex
spring.datasource.password=mypassword123
```

### ⚠️ Security Warning

**Never commit passwords to Git!** Later, you'll use environment variables or `application-secrets.properties` (git-ignored).

For development, it's okay. For production, **always use secrets**.

---

## API Testing Guide

### Method 1: Browser Testing (Simple)

1. **Start backend**: `mvnw.cmd spring-boot:run`
2. **Open browser**: `http://localhost:8080/api/hello`
3. You'll see the response

### Method 2: Postman (Recommended)

**Postman** is a tool to test APIs visually.

#### Download Postman

1. Go to https://www.postman.com/downloads/
2. Download for Windows
3. Install and open

#### Create a Test Request

1. Click "New" → "Request"
2. Name: `Test Hello Endpoint`
3. Method: `GET`
4. URL: `http://localhost:8080/api/hello`
5. Click "Send"
6. See response in bottom panel

#### Example API Endpoints to Test

```
GET     http://localhost:8080/api/hello
GET     http://localhost:8080/api/jobs
POST    http://localhost:8080/api/jobs  (with body)
GET     http://localhost:8080/api/candidates/1
```

### Method 3: Thunder Client (VS Code)

VS Code extension for API testing:

1. Install "Thunder Client" extension
2. Click Thunder Client icon in sidebar
3. Create new request
4. Set URL and method
5. Click "Send"

### Example: Test Hello Endpoint

Create a simple controller in `Backend/demo/src/main/java/com/example/demo/controller/ApiController.java`:

```java
package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello from HireFlow Backend!";
    }
}
```

Then test with:

```
GET http://localhost:8080/api/hello
```

Expected response:

```
Hello from HireFlow Backend!
```

---

## Spring Security Explanation

### Why Do I See a Login Page?

When you run the backend, you see a login page automatically. This is **Spring Security** — a framework that protects your application.

By default, Spring Security:

- ✅ Requires authentication on ALL endpoints
- ✅ Provides a default login page
- ✅ Creates default user: `user` with password in console

### Where is the Default Password?

When you run the backend, look in the console for:

```
Using generated security password: xxxx-xxxx-xxxx-xxxx
```

Copy this password. Use:

- **Username**: `user`
- **Password**: (from console)

### How to Disable for Development

**Option 1: Disable Security Completely** (Quick, for testing)

Create `Backend/demo/src/main/java/com/example/demo/config/SecurityConfig.java`:

```java
package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()  // Disable CSRF protection (for development)
            .authorizeRequests()
                .anyRequest().permitAll();  // Allow all requests without auth

        return http.build();
    }
}
```

**Option 2: Allow Specific Endpoints** (Better practice)

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf().disable()
        .authorizeRequests()
            .antMatchers("/api/**").permitAll()  // Public API endpoints
            .antMatchers("/admin/**").authenticated()  // Protect admin
            .anyRequest().permitAll();

    return http.build();
}
```

After changing `SecurityConfig.java`, **restart the backend**.

### How SecurityConfig Works

| Line                                  | What It Does                                  |
| ------------------------------------- | --------------------------------------------- |
| `@Configuration`                      | Marks this as a Spring configuration class    |
| `@EnableWebSecurity`                  | Enables Spring Security                       |
| `@Bean`                               | Creates a Spring bean                         |
| `.csrf().disable()`                   | Disables CSRF protection (OK for development) |
| `.authorizeRequests()`                | Start security rules                          |
| `.antMatchers("/api/**").permitAll()` | Allow all `/api/*` endpoints                  |
| `.authenticated()`                    | Require login for this endpoint               |
| `.anyRequest().permitAll()`           | Allow everything else                         |

---

## Common Errors & Fixes

### Error 1: "mvnw.cmd is not recognized"

**Cause**: You're not in the right directory.

**Fix**:

```powershell
# ❌ Wrong
cd Backend
mvnw.cmd spring-boot:run

# ✅ Correct
cd Backend/demo
mvnw.cmd spring-boot:run
```

### Error 2: "Java not found" or "JAVA_HOME not set"

**Cause**: Java is not installed or not in PATH.

**Fix**:

```powershell
# Check if Java is installed
java -version

# If not, download from:
# https://www.oracle.com/java/technologies/downloads/
```

After installing, **restart PowerShell** and retry.

### Error 3: "class, interface, enum expected"

**Cause**: Syntax error in Java file (missing semicolon, brace, etc.).

**Example error**:

```
[ERROR] C:\HireFlow\Backend\demo\src\main\java\com\example\demo\ApiController.java:[5,1] class, interface, enum expected
```

**Fix**:

1. Open the file mentioned in error
2. Check line 5
3. Look for missing `;` or `}`
4. Save and rebuild

### Error 4: "Port 8080 already in use"

**Cause**: Another application is using port 8080.

**Fix Option 1**: Kill the process

```powershell
# Find process using port 8080
netstat -ano | findstr :8080

# Kill the process (replace PID with actual number)
taskkill /PID <PID> /F
```

**Fix Option 2**: Use different port

```properties
# In application.properties
server.port=8081
```

### Error 5: "Cannot find module" (npm error)

**Cause**: Frontend dependencies not installed.

**Fix**:

```powershell
cd frontend
npm install
npm run dev
```

### Error 6: "Database connection refused"

**Cause**: Neon credentials wrong, or database offline.

**Fix**:

```powershell
# 1. Check application.properties has correct credentials
# 2. Test connection in Neon dashboard
# 3. Ensure SSL mode is set to "require"
```

### Error 7: "Vite: Cannot find native binding"

**Cause**: Native dependencies corrupted.

**Fix**:

```powershell
cd frontend
rm -r node_modules package-lock.json
npm install
npm run dev
```

---

## Development Workflow

Follow this **exact order** when setting up HireFlow:

### Phase 1: Backend Foundation (Days 1-2)

**Step 1**: Backend Setup

```powershell
cd Backend/demo
mvnw.cmd spring-boot:run
```

- ✅ Backend runs on `http://localhost:8080`
- ✅ See Spring Security login page

**Step 2**: Disable Security for Development

- Create `SecurityConfig.java`
- Allow `/api/**` endpoints
- Restart backend

**Step 3**: Create Test Endpoint

```java
@RestController
@RequestMapping("/api")
public class ApiController {
    @GetMapping("/health")
    public String health() {
        return "Backend is running";
    }
}
```

**Step 4**: Test in Browser

- Visit `http://localhost:8080/api/health`
- See response: `"Backend is running"`

### Phase 2: Database Connection (Days 2-3)

**Step 1**: Create Neon Account & Project

- Sign up at https://neon.tech/
- Create project
- Get connection string

**Step 2**: Update `application.properties`

```properties
spring.datasource.url=jdbc:postgresql://host/dbname?sslmode=require
spring.datasource.username=user
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update
```

**Step 3**: Create Entity (Example: Job)

```java
@Entity
@Table(name = "jobs")
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title;

    // getters and setters
}
```

**Step 4**: Create Repository

```java
@Repository
public interface JobRepository extends JpaRepository<Job, Long> {
}
```

**Step 5**: Create Service

```java
@Service
public class JobService {
    @Autowired
    private JobRepository repository;

    public List<Job> getAllJobs() {
        return repository.findAll();
    }
}
```

**Step 6**: Create REST Controller

```java
@RestController
@RequestMapping("/api/jobs")
public class JobController {
    @Autowired
    private JobService service;

    @GetMapping
    public List<Job> getJobs() {
        return service.getAllJobs();
    }
}
```

**Step 7**: Test API

- POST `http://localhost:8080/api/jobs` (with JSON body)
- GET `http://localhost:8080/api/jobs`
- See data from database

### Phase 3: Frontend Setup (Days 3-4)

**Step 1**: Start Frontend

```powershell
cd frontend
npm run dev
```

- ✅ Frontend runs on `http://localhost:5173`

**Step 2**: Create API Service

```typescript
// src/services/api.ts
const API_BASE = "http://localhost:8080/api";

export async function getJobs() {
  const response = await fetch(`${API_BASE}/jobs`);
  return response.json();
}
```

**Step 3**: Create Component

```typescript
// src/components/JobsList.tsx
import { useEffect, useState } from 'react';
import { getJobs } from '../services/api';

export function JobsList() {
    const [jobs, setJobs] = useState([]);

    useEffect(() => {
        getJobs().then(setJobs);
    }, []);

    return (
        <div>
            {jobs.map(job => (
                <div key={job.id}>{job.title}</div>
            ))}
        </div>
    );
}
```

**Step 4**: Test Frontend

- Open `http://localhost:5173`
- See jobs from backend

### Phase 4: Authentication (Days 5-7)

**Step 1**: Add JWT Dependency

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
```

**Step 2**: Create JWT Utility

```java
@Component
public class JwtProvider {
    private String secretKey = "your-secret-key";

    public String generateToken(String email) {
        // JWT token generation logic
    }

    public String getEmailFromToken(String token) {
        // Extract email from token
    }
}
```

**Step 3**: Update SecurityConfig

```java
http.authorizeRequests()
    .antMatchers("/auth/login", "/auth/register").permitAll()
    .antMatchers("/api/**").authenticated();
```

**Step 4**: Create Auth Controller

```java
@RestController
@RequestMapping("/auth")
public class AuthController {
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // Validate credentials, generate JWT
    }
}
```

---

## Troubleshooting Checklist

### Backend Won't Start

- [ ] Correct directory? (`cd Backend/demo`)
- [ ] Java installed? (`java -version`)
- [ ] Port 8080 free? (check `netstat -ano`)
- [ ] application.properties valid?
- [ ] No syntax errors in Java files?

### Backend Starts but Database Connection Fails

- [ ] Neon credentials correct?
- [ ] Connection string in JDBC format?
- [ ] SSL mode set to `require`?
- [ ] Network connection working?
- [ ] Password doesn't have special characters needing escaping?

### Frontend Won't Start

- [ ] Correct directory? (`cd frontend`)
- [ ] Node.js installed? (`node --version`)
- [ ] Dependencies installed? (`npm install`)
- [ ] Port 5173 free?
- [ ] Node version 18+?

### API Endpoint Returns 401 (Unauthorized)

- [ ] SecurityConfig allows this endpoint?
- [ ] Spring Security not restricting?
- [ ] Try disabling CSRF for development

### Can't Access Frontend from Backend

- [ ] CORS headers configured?
- [ ] Both servers running?
- [ ] Correct URLs in API calls?

---

## Quick Start Commands

### Terminal 1: Backend

```powershell
cd D:\HireFlow\Backend\demo
mvnw.cmd spring-boot:run
```

### Terminal 2: Frontend

```powershell
cd D:\HireFlow\frontend
npm run dev
```

### Test

- Backend: http://localhost:8080/api/health
- Frontend: http://localhost:5173

---

## Next Steps

1. ✅ Follow Phase 1-4 in Development Workflow
2. ✅ Create your first API endpoint
3. ✅ Connect database
4. ✅ Build frontend components
5. ✅ Implement authentication
6. ✅ Deploy to production (later phase)

---

## Getting Help

### Resources

- **Spring Boot Docs**: https://spring.io/projects/spring-boot
- **React Docs**: https://react.dev
- **Neon Docs**: https://neon.tech/docs/introduction
- **PostgreSQL Docs**: https://www.postgresql.org/docs/

### Common Issues

- Google the error message
- Check Spring Boot logs
- Ask on Stack Overflow
- Check GitHub issues for dependencies

---

**Last Updated**: June 18, 2026

**Version**: 1.0

**Maintained By**: Senior Full-Stack Engineer
