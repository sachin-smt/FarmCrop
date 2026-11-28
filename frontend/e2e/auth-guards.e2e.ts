import { expect, test } from '@playwright/test';

/**
 * Route protection and auth pages. These run without the backend: an unreachable /auth/refresh
 * must be treated as "signed out", never as a session.
 */

test.beforeEach(async ({ page }) => {
  // Force "no session" deterministically, whether or not a backend is running.
  await page.route('**/api/v1/auth/refresh', (route) => route.fulfill({
    status: 401,
    contentType: 'application/json',
    body: JSON.stringify({ success: false, code: 'SESSION_EXPIRED', message: 'Your session has expired.' }),
  }));
});

for (const path of ['/admin/dashboard', '/admin/applications', '/seller/dashboard', '/seller/crops/new', '/seller/application', '/profile']) {
  test(`anonymous visitors to ${path} are sent to login`, async ({ page }) => {
    await page.goto(path);
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.locator('input[autocomplete="current-password"]')).toBeVisible();
  });
}

test('login links to registration and back', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('link', { name: 'Create an account' }).click();
  await expect(page).toHaveURL(/\/register$/);
  await expect(page.getByRole('heading', { name: 'Create your FarmCrop account' })).toBeVisible();
  await page.getByRole('link', { name: 'Log in' }).click();
  await expect(page).toHaveURL(/\/login$/);
});

test('registration validates required fields and password confirmation before calling the API', async ({ page }) => {
  let registerCalls = 0;
  await page.route('**/api/v1/auth/register', (route) => { registerCalls += 1; return route.abort(); });

  await page.goto('/register');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByText('This field is required.').first()).toBeVisible();

  await page.getByLabel('Full name').fill('Asha Devi');
  await page.getByLabel('Username').fill('asha');
  await page.getByLabel('Email').fill('asha@example.test');
  await page.getByLabel('Phone number').fill('9876543210');
  await page.getByLabel('Password', { exact: true }).fill('Harvest2026');
  await page.getByLabel('Confirm password').fill('Different99');
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page.getByText('Passwords do not match.')).toBeVisible();
  expect(registerCalls).toBe(0);
});

test('server field errors are shown next to the matching inputs', async ({ page }) => {
  await page.route('**/api/v1/auth/register', (route) => route.fulfill({
    status: 409,
    contentType: 'application/json',
    body: JSON.stringify({
      success: false, code: 'ACCOUNT_EXISTS', message: 'Some details are already registered.',
      errors: [{ field: 'email', message: 'An account with this email already exists.' }],
    }),
  }));

  await page.goto('/register');
  await page.getByLabel('Full name').fill('Asha Devi');
  await page.getByLabel('Username').fill('asha');
  await page.getByLabel('Email').fill('asha@example.test');
  await page.getByLabel('Phone number').fill('9876543210');
  await page.getByLabel('Password', { exact: true }).fill('Harvest2026');
  await page.getByLabel('Confirm password').fill('Harvest2026');
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page.getByText('An account with this email already exists.')).toBeVisible();
  await expect(page.getByText('Some details are already registered.')).toBeVisible();
});

test('registration asks for the emailed code before any session exists, and shows code errors', async ({ page }) => {
  const now = Date.now();
  let verifyBody: Record<string, string> | null = null;
  await page.route('**/api/v1/auth/register', (route) => route.fulfill({
    status: 202,
    contentType: 'application/json',
    body: JSON.stringify({ success: true, data: {
      email: 'asha@example.test', verificationRequired: true,
      expiresAt: new Date(now + 10 * 60_000).toISOString(), resendAvailableAt: new Date(now + 60_000).toISOString(),
    } }),
  }));
  await page.route('**/api/v1/auth/register/verify', (route) => {
    verifyBody = route.request().postDataJSON() as Record<string, string>;
    return route.fulfill({
      status: 400,
      contentType: 'application/json',
      body: JSON.stringify({ success: false, code: 'OTP_INVALID', message: 'That code is not correct. 4 attempts left.' }),
    });
  });

  await page.goto('/register');
  await page.getByLabel('Full name').fill('Asha Devi');
  await page.getByLabel('Username').fill('asha');
  await page.getByLabel('Email').fill('asha@example.test');
  await page.getByLabel('Phone number').fill('9876543210');
  await page.getByLabel('Password', { exact: true }).fill('Harvest2026');
  await page.getByLabel('Confirm password').fill('Harvest2026');
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page.getByRole('heading', { name: 'Verify your email' })).toBeVisible();
  await expect(page.getByText('asha@example.test')).toBeVisible();
  await expect(page).toHaveURL(/\/register$/);
  await expect(page.getByRole('button', { name: /Resend OTP in \d+s/ })).toBeDisabled();
  const verify = page.getByRole('button', { name: 'Verify and create account' });
  await expect(verify).toBeDisabled();

  await page.getByLabel('Email code').fill('123456');
  await verify.click();
  await expect(page.getByText('That code is not correct. 4 attempts left.')).toBeVisible();
  expect(verifyBody).toEqual({ email: 'asha@example.test', otp: '123456', password: 'Harvest2026' });
  expect((await page.context().cookies()).some((cookie) => cookie.name === 'fc_refresh')).toBe(false);

  // "Change details" returns to the filled-in form.
  await page.getByRole('button', { name: 'Change details' }).click();
  await expect(page.getByLabel('Email')).toHaveValue('asha@example.test');
});

test('a username taken before verification can be changed and the same code reused', async ({ page }) => {
  const now = Date.now();
  const verifyBodies: Record<string, string>[] = [];
  await page.route('**/api/v1/auth/register', (route) => route.fulfill({
    status: 202, contentType: 'application/json',
    body: JSON.stringify({ success: true, data: { email: 'ravi@example.test', verificationRequired: true,
      expiresAt: new Date(now + 600_000).toISOString(), resendAvailableAt: new Date(now + 60_000).toISOString() } }),
  }));
  await page.route('**/api/v1/auth/register/verify', (route) => {
    verifyBodies.push(route.request().postDataJSON() as Record<string, string>);
    return route.fulfill({
      status: 409, contentType: 'application/json',
      body: JSON.stringify({ success: false, code: 'ACCOUNT_EXISTS', message: 'Some details are already registered.',
        errors: [{ field: 'username', message: 'This username is already taken.' }] }),
    });
  });

  await page.goto('/register');
  await page.getByLabel('Full name').fill('Ravi Kumar');
  await page.getByLabel('Username').fill('ravi');
  await page.getByLabel('Email').fill('ravi@example.test');
  await page.getByLabel('Phone number').fill('9876543211');
  await page.getByLabel('Password', { exact: true }).fill('Harvest2026');
  await page.getByLabel('Confirm password').fill('Harvest2026');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByLabel('Email code').fill('654321');
  await page.getByRole('button', { name: 'Verify and create account' }).click();

  await expect(page.getByText('This username is already taken.')).toBeVisible();
  await expect(page.getByLabel('Email code')).toHaveValue('654321');
  await page.getByLabel('Username').fill('ravi_k');
  await page.getByRole('button', { name: 'Verify and create account' }).click();
  await expect.poll(() => verifyBodies.length).toBe(2);
  expect(verifyBodies[0]).toEqual({ email: 'ravi@example.test', otp: '654321', password: 'Harvest2026' });
  expect(verifyBodies[1]).toEqual({ email: 'ravi@example.test', otp: '654321', password: 'Harvest2026', username: 'ravi_k' });
});

test('mobile menu offers sign-in', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto('/');
  await page.getByRole('button', { name: 'Open menu' }).click();
  await page.getByRole('navigation', { name: 'Mobile navigation' }).getByRole('link', { name: 'Login / Sign Up' }).click();
  await expect(page).toHaveURL(/\/login$/);
});
