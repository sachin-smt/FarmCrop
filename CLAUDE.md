# FarmCrop — project guide for Claude Code

Agricultural marketplace: farmers (sellers) sell produce directly to buyers; admins verify sellers
and moderate the marketplace. Roadmap and open business decisions: `docs/implementation-plan.md`.

## Repository layout
- `backend/` — Spring Boot 4.0 / Java 25 / Maven (use `./mvnw`), modular monolith in `com.farmcrop.<module>`
  (`auth, user, buyer, seller, verification, admin, crop, cart, order, payment, notification, chatbot, common, config, security`).
- `frontend/` — React 19 + TypeScript + Vite 8 + Tailwind 4 + react-router 7 + i18next. Existing UI/branding must be preserved.
- `docs/` — architecture, API, database docs. Keep them in sync with what is actually implemented.
- Do not touch `FarmCrop.old-main.zip`, `.github/modernize/`, or `frontend/public/` design assets without asking.

## Commands
| Task | Command (from the folder shown) |
|---|---|
| Backend tests + build | `backend/`: `./mvnw -B verify` |
| Backend run (dev, MySQL) | `backend/`: `./mvnw spring-boot:run` (reads git-ignored `backend/.env`) |
| Dependency CVE scan | `backend/`: `./mvnw -Psecurity-scan verify -DskipTests` |
| Frontend unit tests | `frontend/`: `npm test` |
| Frontend type check + build | `frontend/`: `npm run build` |
| Browser E2E (system Chrome) | `frontend/`: `npm run test:e2e` |
| Frontend dependency audit | `frontend/`: `npm audit` |

## Non-negotiable rules
- **Secrets**: never read, print, or commit `backend/.env`, `frontend/.env*` (except `.env.example`), keys or passwords.
  Never put secrets in command-line arguments. The owner fills secrets in themselves.
- **Schema**: only through new Flyway migrations (`backend/src/main/resources/db/migration`). Never edit an applied migration.
  Never drop tables/data or reset the database without explicit approval. Money = `DECIMAL`, never float.
- **Server is the source of truth**: prices, totals, stock, roles and ownership are computed/checked on the backend.
  Never trust IDs, prices or roles sent by the client; derive the user from the authenticated principal.
- **Roles**: every account is BUYER; SELLER only via admin approval; ADMIN only via bootstrap. No public admin registration.
- **Payments**: Razorpay test mode only unless the owner explicitly enables live keys. An order is paid only after
  server-side signature verification / verified webhook. Never simulate payment success.
- **No fake data**: never substitute mock data for a failed API call; show an error/empty state or mark the feature pending.
- **Git**: no force push, no history rewrite, no `reset --hard`, no remote changes. One verified milestone = one commit, then push.

## Conventions
- API base `/api/v1`, envelope `ApiResponse { success, data, message, code, errors }`, pagination `PageResponse`.
- Controllers are thin; business logic and `@Transactional` live in services; DTOs (records) at the API boundary, never entities.
- Throw `ResourceNotFoundException` / `BusinessRuleException` / `ApiException` for expected failures.
- Frontend calls go through `frontend/src/services/apiClient.ts` (`api.get/post/...`); one service file per module
  in `frontend/src/services/`. No hardcoded hosts — `VITE_API_BASE_URL` in prod, Vite proxy in dev.
- Migrations must run on MySQL 8 **and** H2 (MySQL mode) because tests run them; see the `farmcrop-migrations` skill.

## Tools available in this project
- **Context7 MCP**: current library docs. Check the version in `pom.xml` / `package.json` first, then query.
  Send only library names and generic API questions — never project code, data or secrets.
- **Playwright MCP** (system Chrome, isolated profile) for interactive browser checks; **Playwright Test** (`frontend/e2e/*.e2e.ts`) for repeatable ones.
- Project skills: `farmcrop-backend`, `farmcrop-frontend`, `farmcrop-migrations`, `farmcrop-release`.
- Built-in `/code-review` and `/security-review` for reviews before each milestone commit.
