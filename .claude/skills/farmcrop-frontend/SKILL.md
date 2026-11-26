---
name: farmcrop-frontend
description: Use when connecting FarmCrop React pages to the backend, adding pages/forms, or verifying UI behaviour and responsiveness in the browser (Playwright).
---

# FarmCrop frontend integration

## Principles
- Preserve the existing design system: colours (`#0b6b3a`, `#064e2a`, `#e8f5ec`, `#122018`...), `serif` headings,
  rounded-xl/2xl cards, lucide icons, i18next strings. Do not redesign pages that already exist.
- Reuse existing components/types first (`src/features/portals`, `src/components`, `src/types`). Replace demo
  stores (`portalDataService` sessionStorage, `data.ts` crops) with API calls as each milestone lands, and delete
  the demo path only once the real one works.
- Never show mock data when an API call fails. Render loading, empty and error states (with retry where sensible).

## API access
- Only through `src/services/apiClient.ts` (`api.get/post/put/patch/delete`, `ApiError`, `PageResponse`).
- One typed service per module in `src/services/` (`authService.ts`, `cropService.ts`, ...). Types mirror backend DTOs.
- Show `ApiError.fieldErrorMap()` next to form inputs; show `ApiError.message` otherwise.
- Access token lives in memory only; refresh token is an HttpOnly cookie handled by the backend. Never use
  localStorage/sessionStorage for tokens or personal data.
- Uploads: pass a `FormData` body; validate type/size client-side for UX, but the server is authoritative.

## Verification
1. `npm test` (Vitest, `*.test.ts`) — add tests for services and form logic.
2. `npm run build` (type check + bundle).
3. `npm run test:e2e` (Playwright Test, `e2e/*.e2e.ts`, system Chrome) — add a journey test per milestone.
4. For exploratory checks use the Playwright MCP: open the page, fill forms, check the console, and resize to
   360 / 390 / 768 / 1024 / 1440 px looking for horizontal overflow, clipped modals and tiny touch targets.
