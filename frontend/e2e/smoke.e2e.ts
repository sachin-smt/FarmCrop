import { expect, test, type Page } from '@playwright/test';

/** Collects uncaught page errors and console errors so tests can assert a clean page. */
function trackErrors(page: Page) {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text());
  });
  return errors;
}

async function horizontalOverflow(page: Page) {
  return page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
}

test('home page renders without runtime errors', async ({ page }) => {
  const errors = trackErrors(page);
  await page.goto('/');
  await expect(page.getByRole('link', { name: 'FarmCrop home' })).toBeVisible();
  expect(errors).toEqual([]);
});

test('login page shows the username/email form', async ({ page }) => {
  await page.goto('/login');
  await expect(page.locator('input[autocomplete="username"]')).toBeVisible();
  await expect(page.locator('input[autocomplete="current-password"]')).toBeVisible();
});

test('unknown routes show the 404 page', async ({ page }) => {
  await page.goto('/definitely-not-a-page');
  await expect(page.getByText('Page not found')).toBeVisible();
});

for (const width of [360, 390, 768, 1024, 1440]) {
  test(`no horizontal overflow on public pages at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    for (const path of ['/', '/marketplace', '/login']) {
      await page.goto(path);
      await page.waitForLoadState('networkidle');
      expect(await horizontalOverflow(page), `overflow on ${path} at ${width}px`).toBeLessThanOrEqual(0);
    }
  });
}
