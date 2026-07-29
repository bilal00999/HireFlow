# **HireFlow — AI-Powered HR Platform**

---

## **Project Overview**

HireFlow is an end-to-end recruitment automation platform that replaces fragmented hiring tools (LinkedIn, Indeed, manual emails, Zoom interviews) with a single unified system.

---

## **The Problem It Solves**

Today's hiring process looks like this:

- HR posts on LinkedIn/Indeed → waits
- Manually reviews hundreds of resumes → days lost
- Emails assessments → candidates ghost or forget
- Schedules interviews → back-and-forth chaos
- Compiles scores manually → errors and bias

### **HireFlow Solution**

HireFlow collapses all of this into one automated recruitment pipeline.

---

## **How It Works (End-to-End Flow)**

HR creates job → Candidate applies → ATS scans resume
↓ pass ↓ fail
Email sent with Auto-rejection email sent
24hr assessment link
↓ pass
AI conducts video/text interview
↓ pass
Scores emailed to HR
↓
HR makes final decision

Everything runs based on rules defined by HR for each job posting.

---

## **Documentation Index**

| File                         | What It Covers                                           |
| ---------------------------- | -------------------------------------------------------- |
| `01-PRODUCT-REQUIREMENTS.md` | Full feature list, user stories, acceptance criteria     |
| `02-SYSTEM-ARCHITECTURE.md`  | Tech stack, services, system design                      |
| `03-DATABASE-DESIGN.md`      | Database schema, entities, ER diagram                    |
| `04-API-DESIGN.md`           | REST APIs with request/response formats                  |
| `05-FRONTEND-GUIDE.md`       | React app structure, pages, components, state management |
| `06-BACKEND-GUIDE.md`        | Spring Boot architecture, services, security             |
| `07-AI-INTEGRATION.md`       | ATS resume scoring and AI interview system               |
| `08-EMAIL-SYSTEM.md`         | Email templates and automation flow                      |
| `09-DEVELOPMENT-ROADMAP.md`  | Development phases and milestones                        |
| `10-TOOLS-AND-SETUP.md`      | Tools, installation, and setup guide                     |

---

## **Tech Stack Summary**

| Layer          | Technology                              |
| -------------- | --------------------------------------- |
| Frontend       | React 18 + TypeScript + Tailwind CSS    |
| Backend        | Java 21 + Spring Boot 3                 |
| Database       | PostgreSQL                              |
| AI / LLM       | OpenAI GPT-4o (ATS + Interview system)  |
| Resume Parsing | Apache PDFBox + custom NLP              |
| Email          | Mailgun                                 |
| File Storage   | AWS S3 (resumes, recordings)            |
| Authentication | Spring Security + JWT                   |
| Real-time      | WebSockets (interview session)          |
| Queue          | Redis + Spring Scheduler (token expiry) |
| Deployment     | Docker + Docker Compose                 |

---

## **Two Types of Users**

### **HR / Company**

- Creates and manages job/internship postings
- Sets rules: required skills, minimum ATS score, assessment questions, interview topics
- Receives final candidate scores via email
- Views applicant pipeline dashboard

---

### **Candidate / Applicant**

- Browses and applies for jobs
- Uploads resume/CV
- Receives assessment links via email
- Takes AI-conducted interview in the browser
- Gets notified of each stage outcome

---
