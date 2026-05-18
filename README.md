# SpotFinder Backend

Backend service for **SpotFinder** — a platform for discovering and sharing skate, BMX, scooter, and roller spots.

## Current Status

The project is currently in active MVP development.

Implemented:

- User registration
- User login with JWT
- Current user profile endpoint
- User profile update
- Spot creation
- Spot approval workflow
- Spot admin/moderation endpoints
- Global exception handling
- Swagger/OpenAPI documentation
- PostgreSQL database with Flyway migrations
- Docker-based stage deployment
- CI/CD via Gitea Actions
- GitHub mirror for portfolio visibility

## Tech Stack

- Java 21
- Spring Boot
- Spring Web
- Spring Security
- JWT
- Spring Data JPA
- Hibernate
- PostgreSQL
- Flyway
- Gradle
- Docker
- Gitea Actions
- Swagger / OpenAPI

## API Documentation

Stage Swagger UI:

    http://stage-api.deployflow.ru/swagger-ui/index.html

Stage is available through VPN.

## Main API Endpoints

### Auth

    POST /api/v1/auth/register
    POST /api/v1/auth/login

### Users

    GET   /api/v1/user/me
    PATCH /api/v1/user/me

### Spots

    POST /api/v1/spots
    GET  /api/v1/spots
    GET  /api/v1/spots/{spotId}

User-created spots are not approved by default.

### Admin / Moderation

    GET    /api/v1/admin/spots
    GET    /api/v1/admin/spots/{spotId}
    POST   /api/v1/admin/spots
    POST   /api/v1/admin/spots/{spotId}/approve
    DELETE /api/v1/admin/spots/{spotId}

Admin-created spots are approved automatically.

## Authentication

The API uses JWT Bearer authentication.

Example header:

    Authorization: Bearer <accessToken>

Public endpoints:

    POST /api/v1/auth/register
    POST /api/v1/auth/login
    GET  /actuator/health
    GET  /swagger-ui/**
    GET  /v3/api-docs/**

All other endpoints require authentication.

Role-based access is used for moderation:

- `USER`
- `MODERATOR`
- `ADMIN`

## Spot Domain

A spot has a general type and a set of features.

Example spot type:

    STREET
    SKATEPARK
    PUMP_TRACK
    BOWL
    DIY
    OTHER

Example spot features:

    FLAT
    RAIL
    LEDGE
    STAIRS
    GAP
    BANK
    MANUAL_PAD
    MINI_RAMP
    HALFPIPE
    QUARTER_PIPE
    FUNBOX
    PYRAMID
    BOWL
    CURB
    WALLRIDE
    LIGHTING
    COVERED
    OTHER

Example create spot request:

    {
      "name": "Central Plaza",
      "description": "Flat area with stairs and ledges",
      "latitude": 55.751244,
      "longitude": 37.618423,
      "type": "STREET",
      "features": [
        "FLAT",
        "STAIRS",
        "LEDGE"
      ]
    }

## Local Development

### Requirements

- Java 21
- Git
- Gradle Wrapper
- Docker / Docker Compose
- PostgreSQL or Dockerized PostgreSQL
- Postman or another API client

### Run tests

Linux / macOS:

    ./gradlew clean test

Windows:

    .\gradlew.bat clean test

### Apply formatting

Linux / macOS:

    ./gradlew spotlessApply

Windows:

    .\gradlew.bat spotlessApply

### Run checks

Linux / macOS:

    ./gradlew spotlessCheck checkstyleMain checkstyleTest test

Windows:

    .\gradlew.bat spotlessCheck checkstyleMain checkstyleTest test

### Build application

Linux / macOS:

    ./gradlew bootJar

Windows:

    .\gradlew.bat bootJar

## Database

Database schema is managed by Flyway migrations.

Migration location:

    src/main/resources/db/migration

Current domain tables include:

- `users`
- `spots`
- `spot_features`

## CI/CD

The project uses Gitea Actions for stage deployment.

Flow:

    push to develop
    -> run checks and tests
    -> build JAR
    -> build Docker image
    -> deploy to stage
    -> run healthcheck

Main working branch:

    develop

GitHub is used as a mirror/portfolio repository. Development happens in Gitea.

## Project Structure

    src/main/java/com/spotfinder
    ├── auth
    │   ├── controller
    │   ├── dto
    │   ├── security
    │   └── service
    ├── user
    │   ├── controller
    │   ├── dto
    │   ├── entity
    │   ├── repository
    │   └── service
    ├── spot
    │   ├── controller
    │   ├── dto
    │   ├── entity
    │   ├── repository
    │   └── service
    └── common
        ├── entity
        ├── error
        └── exception

## Current Development Flow

    local machine
    -> write code
    -> run tests
    -> commit
    -> push to Gitea develop
    -> Gitea Actions deploys to stage
    -> verify through Swagger/Postman

Do not change backend code manually on the server. All backend changes should go through Git and CI/CD.

## Roadmap

Planned next steps:

- Improve OpenAPI documentation
- Add pagination for spots
- Add filters by spot type and features
- Add nearby spots search
- Add user favorites
- Add check-ins
- Add friends system
- Add Telegram bot integration
- Add web admin/QA frontend
- Add iOS client