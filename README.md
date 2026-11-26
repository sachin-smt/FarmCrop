# FarmCrop

FarmCrop is a farmer-to-buyer agricultural marketplace project. It brings
together a React frontend and a separately developed Spring Boot backend, with
MySQL planned for local development.

## Features and status

- **Buyer marketplace:** Homepage and demo crop listings with search, category
  filters, and a frontend-only cart are present. Listings and prices are sample
  content; checkout and payments are not connected.
- **Seller experience:** Farmer onboarding, seller application/status screens,
  and a protected dashboard placeholder are present. Seller workflows require
  the future backend.
- **Admin:** No admin frontend is present yet. The backend admin package is an
  empty structural placeholder.
- **Authentication:** Login and phone-verification screens are present, but
  OAuth, OTP, and session operations are intentionally unavailable until a
  backend is connected.
- **AI chatbot:** The homepage includes the Krishi Saathi chat UI, quick
  questions, and voice input. The current response service is a local
  development placeholder, not a connected AI provider.

## Technology stack

### Frontend (existing)

- React 19 and TypeScript 7
- Vite 8
- Tailwind CSS 4
- React Router 7
- i18next and react-i18next
- Lucide React

Exact dependency versions are recorded in [frontend/package-lock.json](frontend/package-lock.json).

### Backend (scaffold)

- Java 25 and Spring Boot 4.0.10
- Maven, Spring Web MVC, Spring Data JPA, and Hibernate
- Spring Security, Bean Validation, Actuator, and Springdoc OpenAPI
- MySQL with Flyway

The backend is developed separately from the frontend. Its feature packages
and SQL migrations are placeholders; there are no implemented APIs or database
tables. Hibernate schema generation and Flyway execution are disabled.

## Project structure

```text
FarmCrop/
├── backend/       # Spring Boot backend scaffold
├── frontend/      # Existing React + TypeScript application
├── database/      # Database notes and sample-data placeholder
├── docs/          # Project and architecture documentation
├── .github/       # Repository modernization configuration
├── .vscode/       # Workspace settings
├── .gitignore
└── README.md
```

## Run the frontend

Requirements: Node.js and npm. From the repository root:

```powershell
cd frontend
npm ci
npm run dev
```

Create a production build with `npm run build`, or serve it locally with
`npm run preview`. `frontend/.env.example` contains a blank
`VITE_API_BASE_URL`; no backend URL or credentials are configured.

## Backend and database status

The backend scaffold is in `backend/`. Running it requires JDK 25, Maven 3.10
or later, a local MySQL server, and a database you create yourself. For local
development in PowerShell, set credentials in the session and start the dev
profile:

```powershell
$env:DB_USERNAME = "your-local-mysql-user"
$env:DB_PASSWORD = "your-local-mysql-password"
mvn -f backend\pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

The dev profile defaults to `localhost:3306/farmcrop`; override `DB_HOST`,
`DB_PORT`, or `DB_NAME` as needed. No cloud database is configured, and the
scaffold creates no databases or tables.

## Development workflow

The frontend currently runs independently with local demo content. Once API
contracts and backend endpoints are ready, the React application can call the
Spring Boot REST API using the frontend environment configuration. Authentication,
marketplace persistence, orders, payments, and AI provider integration remain
separate future work.

See [docs/requirements.md](docs/requirements.md),
[docs/architecture.md](docs/architecture.md), and
[docs/ai-chatbot-design.md](docs/ai-chatbot-design.md) for planned capabilities.