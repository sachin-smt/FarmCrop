# Architecture

FarmCrop is planned as a modular monolith: one Spring Boot application and
deployment, organized into feature-based packages. It is not a microservices
system.

## Backend package responsibilities

- `auth`: future authentication endpoints, services, and DTOs.
- `user`: shared user data and user-facing operations.
- `buyer`: buyer-specific workflows.
- `seller`: seller-specific workflows and listings ownership.
- `crop`: crop catalog and listing workflows.
- `order` and `payment`: order and payment workflows.
- `admin`: administrative workflows.
- `verification`: seller verification application and review workflows.
- `notification`: future user notifications.
- `chatbot`: independent buyer/seller conversation and AI-provider boundary.
- `common`: shared responses, exceptions, and enums.
- `config` and `security`: application configuration and security boundaries.

Each feature owns its own controller, service, repository, entity, and DTO
packages where applicable. The scaffold does not yet implement these layers.

## Request and persistence flow

The intended request flow is:

`Controller -> Service -> Repository -> MySQL database`

Controllers will handle HTTP concerns, services will coordinate feature
behavior, and repositories will provide persistence access through Spring Data
JPA. Cross-feature access should use explicit service interfaces rather than
mixing feature implementations.

## Frontend communication

The future React/TypeScript frontend will communicate with the backend over
documented REST APIs. The frontend is a placeholder only and has not been
initialized.

## Database and migrations

MySQL is intended for local development. Flyway migration files currently
contain comments only; Flyway execution and Hibernate schema generation are
disabled so the scaffold creates no tables.
