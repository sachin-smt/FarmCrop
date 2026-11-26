# FarmCrop

FarmCrop is a farmer-to-buyer agricultural marketplace. This repository contains
the initial backend scaffold and project documentation; feature implementation
has not started.

## Objectives

- Give buyers a place to discover crops, order products, and contact sellers.
- Let sellers manage listings and orders and submit verification documents.
- Provide administrators with marketplace and seller-verification workflows.
- Reserve an independent backend module for future buyer and seller AI assistance.

## Technology stack

- Java 25 and Spring Boot 4.0.10
- Maven, Spring Web MVC, Spring Data JPA, and Hibernate
- MySQL with Flyway (migrations are disabled until schema work is approved)
- Spring Security, Bean Validation, Actuator, and Springdoc OpenAPI
- JUnit 5 and Mockito through Spring Boot's test starter
- Future frontend: React and TypeScript (not initialized)

## Current development status

This is a structure-only scaffold. There are no implemented APIs, authentication
flows, business rules, database tables, or AI provider integrations. The SQL
migration files are comments-only placeholders.

## Running the backend

Requirements: JDK 25, Maven 3.10 or later, and a local MySQL server. Create the
`farmcrop` database yourself; the application does not create databases or
tables. No cloud database is configured.

Set credentials in the current PowerShell session (do not put them in source
files):

```powershell
$env:DB_USERNAME = "your-local-mysql-user"
$env:DB_PASSWORD = "your-local-mysql-password"
mvn -f backend\pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

The development profile defaults to `localhost:3306/farmcrop`. Override
`DB_HOST`, `DB_PORT`, or `DB_NAME` in the environment if needed. Database
credentials are required when starting the application. Hibernate schema
generation and Flyway execution are disabled in this scaffold.

Run the backend tests with:

```powershell
mvn -f backend\pom.xml test
```

See [docs/architecture.md](docs/architecture.md) and
[docs/requirements.md](docs/requirements.md) for the initial design notes.