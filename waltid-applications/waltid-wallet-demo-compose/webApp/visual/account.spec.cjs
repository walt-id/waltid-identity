const { test, expect } = require('@playwright/test');
const { createHash } = require('node:crypto');
const catalogue = require('./catalogue.json');

// Compose's ARIA mirror supplies bounds, while the canvas receives the actual pointer input.
async function clickControl(page, locator) {
  await expect(locator).toBeVisible();
  const box = await locator.boundingBox();
  expect(box).not.toBeNull();
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
}

async function waitForSettledImage(page) {
  let previous;
  let unchangedSince;
  // CSS animation controls do not stop Compose's canvas animations. Two equal
  // frames can occur before the enabled-state color transition has finished.
  await expect.poll(async () => {
    const pixels = await page.screenshot({ animations: 'disabled', caret: 'hide' });
    const current = createHash('sha256').update(pixels).digest('hex');
    const now = performance.now();
    if (current !== previous) {
      previous = current;
      unchangedSince = now;
    }
    return now - unchangedSince >= 500;
  }, { timeout: 10000, intervals: [100] }).toBe(true);
}

for (const scenario of catalogue.states) {
  test(scenario.id, async ({ page }, testInfo) => {
    testInfo.annotations.push({ type: 'requirements', description: catalogue.requirements.join(', ') });
    await page.setViewportSize({ width: scenario.width, height: scenario.height });
    await page.addInitScript(expired => {
      localStorage.setItem('waltid.wallet2.baseUrl', location.origin);
      if (expired) {
        localStorage.setItem('waltid.wallet2.token', 'synthetic-expired-token');
        localStorage.setItem('waltid.wallet2.walletId', 'synthetic-wallet');
      }
    }, scenario.state === 'expired');
    let pending;
    let requests = 0;
    let release;
    const responseReady = new Promise(resolve => { release = resolve; });
    await page.route(url => /^\/(auth|wallet)(\/|$)/.test(url.pathname), async route => {
      pending = route.request();
      requests++;
      if (!['empty', 'expired'].includes(scenario.state)) await responseReady;
      await route.fulfill({ status: scenario.state === 'registered' ? 409 : 401,
        contentType: 'application/json', body: '{}' });
    });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    try {
      await page.goto('/');
      const signIn = page.getByRole('button', { name: 'Sign in', exact: true });
      await expect(signIn).toBeVisible();
      if (!['empty', 'expired'].includes(scenario.state)) {
        const email = page.getByRole('textbox', { name: 'Email', exact: true });
        const password = page.getByRole('textbox', { name: 'Password', exact: true });
        await expect(password).toBeVisible();
        await email.fill('wallet@example.test');
        await password.fill('synthetic-password');
        // The browser's hidden input owns focus; the ARIA mirror is not document.activeElement.
        await password.press('Tab');
        const submit = scenario.state === 'registered'
          ? page.getByRole('button', { name: 'Create account', exact: true }) : signIn;
        await clickControl(page, submit);
        await expect.poll(() => pending?.method()).toBe('POST');
        expect(new URL(pending.url()).pathname).toBe(`/auth/${scenario.state === 'registered' ? 'register' : 'emailpass'}`);
        expect(pending.postDataJSON()).toEqual({ email: 'wallet@example.test', password: 'synthetic-password' });
        await expect(page.getByRole('button', { name: 'Working…', exact: true })).toBeVisible();
        if (scenario.state !== 'busy') {
          await waitForSettledImage(page);
          release();
        }
      }
      if (scenario.state === 'busy') {
        await expect(page.getByRole('button', { name: 'Working…', exact: true })).toBeVisible();
        await clickControl(page, page.getByRole('button', { name: 'Working…', exact: true }));
      } else if (scenario.state === 'invalid') {
        await expect(page.getByText('Invalid email or password.', { exact: true })).toBeVisible();
      } else if (scenario.state === 'registered') {
        await expect(page.getByText('An account with this email already exists. Sign in instead.', { exact: true })).toBeVisible();
      } else if (scenario.state === 'expired') {
        await expect(page.getByText('Your session has expired. Sign in again to continue.', { exact: true })).toBeVisible();
        expect(await page.evaluate(() => localStorage.getItem('waltid.wallet2.token'))).toBeNull();
      } else {
        await clickControl(page, signIn);
      }
      expect(errors).toEqual([]);
      if (scenario.state !== 'busy') {
        await expect(signIn).toBeVisible();
        await expect(page.getByRole('button', { name: 'Working…', exact: true })).toHaveCount(0);
      }
      await waitForSettledImage(page);
      await expect(page).toHaveScreenshot(`${scenario.id}.png`, { animations: 'disabled', caret: 'hide' });
      if (scenario.state === 'empty') expect(requests).toBe(0);
      if (scenario.state === 'busy') expect(requests).toBe(1);
      await testInfo.attach(scenario.id, { body: await page.screenshot(), contentType: 'image/png' });
    } finally {
      release();
      await page.unrouteAll({ behavior: 'wait' });
    }
  });
}
