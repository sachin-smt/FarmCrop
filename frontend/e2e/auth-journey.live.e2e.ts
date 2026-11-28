import { expect, test } from '@playwright/test';

/**
 * Full authentication journey against the REAL backend + MySQL (no mocks).
 * Start the backend first (backend/: ./mvnw spring-boot:run), then:
 *   E2E_LIVE_BACKEND=1 npx playwright test e2e/auth-journey.live.e2e.ts --project=desktop
 * Creates a uniquely named buyer account in the development database.
 */
test.skip(!process.env.E2E_LIVE_BACKEND, 'Set E2E_LIVE_BACKEND=1 with the backend running to execute live journeys.');

/*
 * Registration now requires a 6-digit code from a real inbox (FarmCrop never exposes or simulates it), so this live
 * journey stops at the code screen. It needs SMTP configured on the backend (MAIL_* / EMAIL_OTP_HMAC_SECRET);
 * with email disabled the backend answers 503 and this test reports that instead of pretending.
 * The rest of the session journey (reload, logout, login) is covered by backend integration tests.
 */
test('registration sends a code and no session exists until it is verified', async ({ page }) => {
  const suffix = Date.now().toString(36);
  const username = `e2e_${suffix}`;
  const email = process.env.E2E_REGISTRATION_EMAIL ?? `${username}@example.test`;
  const password = 'Harvest2026';

  await page.goto('/register');
  await page.getByLabel('Full name').fill('E2E Buyer');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Phone number').fill(`9${String(Date.now()).slice(-9)}`);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByLabel('Confirm password').fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page.getByRole('heading', { name: 'Verify your email' })).toBeVisible();
  expect((await page.context().cookies()).some((cookie) => cookie.name === 'fc_refresh')).toBe(false);

  // Logging in before verification is refused with a clear message.
  await page.goto('/login');
  await page.locator('input[autocomplete="username"]').fill(username);
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByText(/verify your email address/i)).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
});
