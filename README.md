# PlanBana Backend (Spring Boot + MongoDB)

This project provides the backend for your Netlify-hosted frontend.

## Features
- JWT auth (access + refresh) in httpOnly cookies
- Email verification & password reset (SMTP configurable)
- Users, Profiles, Events (with geo search), Destinations, Messages (1:1 chat via WebSocket/STOMP)
- Rate limiting (Bucket4j), Audit fields, OpenAPI/Swagger UI
- MongoDB migrations with Mongock
- CORS set for local dev and Netlify

## Quick Start
1. Ensure Java 21 and Maven are installed.
2. Start MongoDB locally or with Docker (`docker-compose up -d mongo mongo-express`).
3. Configure `application.yml` (JWT secret, mail host, allowed origins).
4. Run:
   ```bash
   mvn spring-boot:run
   ```
5. Swagger UI: `http://localhost:8080/swagger-ui/index.html`

See `/docs/API.md` for routes.
