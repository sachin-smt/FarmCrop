# FarmCrop frontend

The existing FarmCrop frontend is a responsive React and TypeScript application
built with Vite, Tailwind CSS, React Router, i18next, and Lucide icons.

## Requirements and commands

Use Node.js and npm, then run from this directory:

```powershell
npm ci
npm run dev
```

Create a production build with `npm run build`; preview it with
`npm run preview`. The checked-in `package-lock.json` is the dependency source
of truth.

## Current routes

- `/` - Homepage, demo crop sections, cart, and Krishi Saathi chatbot.
- `/marketplace` - Searchable and filterable demo crop listings.
- `/farmers` - Farmer onboarding information.
- `/login` and `/verify-phone` - Authentication screens; authentication is not
  connected until the backend is available.
- `/profile`, `/seller/apply`, `/seller/application-status`, and `/seller` -
  Profile and seller screens protected by the current frontend auth placeholder.

## Integration status

Crop listings, prices, insights, and assistant responses are demo content.
Cart state is frontend-only, and payment is not connected. Login, OTP, and
seller access require the future Spring Boot backend. No backend URL is assumed;
`VITE_API_BASE_URL` in `.env.example` is intentionally blank.

The chatbot UI, quick questions, and voice input are preserved. Its current
response service is a local development placeholder; there is no AI provider
connection.
