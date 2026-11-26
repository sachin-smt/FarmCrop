# FarmCrop

FarmCrop is a farmer-to-buyer agricultural marketplace project. It brings
together a React frontend and a separately developed Spring Boot backend, with
MySQL planned for local development.

## Features and status

- **Buyer marketplace:** Homepage and demo crop listings with search, category
  filters, and a frontend-only cart are present. Listings and prices are sample
  content; checkout and payments are not connected.
- **Seller experience:** The development-only Seller portal includes an
  application and status flow, Admin review feedback, and crop-listing
  management after approval. Orders, customers, and earnings are UI
  placeholders because no backend data is connected.
- **Admin:** The development-only Admin portal includes seller application
  review and local demo management screens. Admin decisions can approve,
  reject, request resubmission, or suspend a seller in the current browser
  session.
- **Authentication:** Buyer Google, Facebook, and mobile OTP options remain
  placeholders. Username/email and password are available for the local
  Admin/Seller demo only; this is not production authentication and is disabled
  in production builds. Password recovery awaits backend integration.
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
- Maven Wrapper, Spring Web MVC, Spring Data JPA, and Hibernate
- Spring Security, Bean Validation, Actuator, and Springdoc OpenAPI
- MySQL with Flyway, plus an isolated H2 test profile

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

The Admin/Seller demo can be opened at `/admin/login` or `/seller/login`.
Applications, review decisions, and crop listings are stored in the browser's
`sessionStorage` and are cleared when that browser session ends. Document
selection only previews the local filename; file contents are not uploaded or
stored. Use sample information only and do not enter real personal data or
identity documents.

The Buyer login page retains its Google, Facebook, and mobile OTP options and
adds a role-selecting Admin/Seller password login. It uses temporary
development-only authentication; it is not secure production authentication.
Password recovery displays an integration-pending notice and does not send
email. The centralized frontend auth service is the intended replacement point
for future Spring Boot, Spring Security, and JWT integration.

Run the frontend tests with `npm test`. Create a production build with
`npm run build`, or serve it locally with `npm run preview`.
`frontend/.env.example` contains a blank `VITE_API_BASE_URL`; no backend URL
or credentials are configured.

## Backend and database status

The backend is a standard Maven project rooted at `backend/pom.xml`, with Java
sources in `backend/src/main/java/com/farmcrop/`, resources and Flyway files in
`backend/src/main/resources/`, and tests in `backend/src/test/`. Open the
backend folder or its `pom.xml` in IntelliJ IDEA and import it as a Maven
project; Maven manages the source roots and External Libraries.

The project targets Java 25 (JDK 25.0.1 is installed) and its Maven Wrapper
pins Maven 3.10.0. From PowerShell:

```powershell
cd backend
$env:DB_HOST = "localhost"
$env:DB_PORT = "3306"
$env:DB_NAME = "farmcrop"
$env:DB_USERNAME = "your-local-mysql-user"
$env:DB_PASSWORD = "your-local-mysql-password"
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Tests activate the `test` profile and use an in-memory H2 database, not the
local development database. The MySQL development profile reads connection
values from environment variables. No cloud database is configured, and no
database is created by the scaffold. Existing Flyway scripts are placeholders;
Flyway remains disabled until real schema migrations and the database history
are reviewed.

## Development workflow

The frontend currently runs independently with local demo content. Admin/Seller
portal records exist only in the current browser session and are not shared with
the backend. Once API contracts and backend endpoints are ready, the React
application can call the Spring Boot REST API using the frontend environment
configuration. Production authentication, marketplace persistence, orders,
payments, and AI provider integration remain separate future work.

See [docs/requirements.md](docs/requirements.md),
[docs/architecture.md](docs/architecture.md), and
[docs/ai-chatbot-design.md](docs/ai-chatbot-design.md) for planned capabilities.