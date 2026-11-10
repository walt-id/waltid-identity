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

async function fillControl(page, locator, value, inputMode) {
  await clickControl(page, locator);
  // ARIA textboxes are contenteditable mirrors. Filling them can race the
  // canvas focus change without delivering text to Compose's backing editor.
  const editor = page.locator('input.compose-backing-field');
  await expect(editor).toHaveAttribute('inputmode', inputMode);
  await expect(editor).toBeFocused();
  await editor.fill(value);
  await expect(editor).toHaveValue(value);
}

async function waitForSettledImage(page, clip) {
  let previous;
  let unchangedSince;
  // CSS animation controls do not stop Compose's canvas animations. Two equal
  // frames can occur before the enabled-state color transition has finished.
  await expect.poll(async () => {
    const pixels = await page.screenshot({ animations: 'disabled', caret: 'hide', ...(clip ? { clip } : {}) });
    const current = createHash('sha256').update(pixels).digest('hex');
    const now = performance.now();
    if (current !== previous) {
      previous = current;
      unchangedSince = now;
    }
    return now - unchangedSince >= 500;
  }, { timeout: 10000, intervals: [100] }).toBe(true);
}

async function submitEditedForm(page, locator) {
  await page.keyboard.press('Tab');
  // Filling the backing editor precedes Compose's rendered form/enabled state.
  // Settle the action without waiting on a blinking canvas caret, then click once.
  const box = await locator.boundingBox();
  expect(box).not.toBeNull();
  await waitForSettledImage(page, box);
  await clickControl(page, locator);
}

for (const scenario of catalogue.states.filter(state => state.state !== 'credential-dates')) {
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
        await fillControl(page, email, 'wallet@example.test', 'email');
        await fillControl(page, password, 'synthetic-password', 'password');
        const submit = scenario.state === 'registered'
          ? page.getByRole('button', { name: 'Create account', exact: true }) : signIn;
        await submitEditedForm(page, submit);
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

for (const scenario of catalogue.states.filter(state => state.state === 'credential-dates')) {
  test.describe(scenario.id, () => {
    test.use({ locale: scenario.locale, timezoneId: scenario.timezoneId });
    test(scenario.id, async ({ page }, testInfo) => {
      await page.setViewportSize({ width: scenario.width, height: scenario.height });
      testInfo.annotations.push({ type: 'requirements', description: scenario.requirements.join(', ') });
      await page.addInitScript(() => {
        localStorage.setItem('waltid.wallet2.baseUrl', location.origin);
        localStorage.setItem('waltid.wallet2.token', 'synthetic-browser-token');
        localStorage.setItem('waltid.wallet2.walletId', 'synthetic-wallet');
        localStorage.setItem('waltid.wallet2.walletTargets', JSON.stringify(['synthetic-wallet']));
      });
      const metadata = {
        id: 'synthetic-date-credential', format: 'jwt_vc_json', label: 'Date display credential',
        issuer: 'Synthetic Issuer', subject: 'did:example:holder', addedAt: '2026-10-25T00:35:00Z',
      };
      const responses = {
        '/wallet': ['synthetic-wallet'],
        '/wallet/synthetic-wallet': { walletId: 'synthetic-wallet', defaultKeyId: 'synthetic-key', defaultDidId: 'did:example:holder' },
        '/wallet/synthetic-wallet/dids': [{ did: 'did:example:holder' }],
        '/wallet/synthetic-wallet/credentials': [metadata],
        '/wallet/synthetic-wallet/credentials/receive/deferred': [],
        '/wallet/synthetic-wallet/credentials/synthetic-date-credential': {
          ...metadata,
          credential: {
            ...metadata,
            credentialData: {
              // Both instants are 02:30 in Budapest, on opposite sides of the DST overlap.
              iat: 1792888200, exp: 1792891800,
              validFrom: '2026-10-25T00:30:00.123456789Z',
              credentialSubject: { birth_date: '1990-05-21' },
            },
          },
        },
      };
      const unexpected = [];
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.route(url => url.pathname === '/wallet' || url.pathname.startsWith('/wallet/'), async route => {
        const path = new URL(route.request().url()).pathname;
        if (path.endsWith('/set-default') && route.request().method() === 'PUT') {
          await route.fulfill({ status: 204 });
        } else if (route.request().method() === 'GET' && Object.hasOwn(responses, path)) {
          await route.fulfill({ json: responses[path] });
        } else {
          unexpected.push(`${route.request().method()} ${path}`);
          await route.fulfill({ status: 404, json: {} });
        }
      });
      await page.goto('/');
      await clickControl(page, page.getByText('Date display credential', { exact: true }));
      const birthDate = page.getByText('1990-05-21', { exact: true });
      await expect(birthDate).toHaveCount(1);
      // Wide cards put the details below the fold. Scroll the actual canvas, not its ARIA mirror.
      if (!await birthDate.isVisible()) {
        await page.mouse.move(scenario.width / 2, scenario.height / 2);
        await page.mouse.wheel(0, scenario.height);
      }
      await expect(birthDate).toBeVisible();
      await clickControl(page, page.getByRole('button', { name: 'Technical details', exact: true }));
      // Assertions exercise the real API2 mapping, normalizer, Intl adapter and claim component.
      // Bidi isolates keep the parenthesized zone readable in either text direction.
      for (const value of [
        '25 Oct 2026, 02:30 \u2068(Budapest, CEST)\u2069',
        '25 Oct 2026, 02:30 \u2068(Budapest, CET)\u2069',
        '25 Oct 2026, 02:30:00.123456789 \u2068(Budapest, CEST)\u2069',
        '25 Oct 2026, 02:35 \u2068(Budapest, CEST)\u2069',
      ]) {
        await expect(page.getByText(value, { exact: true })).toBeVisible();
      }
      await waitForSettledImage(page);
      expect(unexpected).toEqual([]);
      expect(errors).toEqual([]);
      await expect(page).toHaveScreenshot(`${scenario.id}.png`, { animations: 'disabled', caret: 'hide' });
      await testInfo.attach(scenario.id, { body: await page.screenshot(), contentType: 'image/png' });
    });
  });
}

test('immediate registration failure restores editable controls and retry', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('waltid.wallet2.baseUrl', location.origin));
  const submitted = [];
  await page.route('**/auth/register', async route => {
    submitted.push(route.request().postDataJSON());
    await route.fulfill({ status: 409, contentType: 'application/json', body: '{}' });
  });
  await page.goto('/');
  const email = page.getByRole('textbox', { name: 'Email', exact: true });
  const password = page.getByRole('textbox', { name: 'Password', exact: true });
  const register = page.getByRole('button', { name: 'Create account', exact: true });
  await expect(email).toBeVisible();
  await fillControl(page, email, 'wallet@example.test', 'email');
  await fillControl(page, password, 'synthetic-password', 'password');
  await submitEditedForm(page, register);
  await expect(page.getByText('An account with this email already exists. Sign in instead.', { exact: true })).toBeVisible();

  // Use actual canvas pointer/backing-editor input to prove recovery; the ARIA mirror's
  // enabled attribute does not establish that a Compose field accepts edits.
  await fillControl(page, email, 'retry@example.test', 'email');
  await fillControl(page, password, 'retry-password', 'password');
  await submitEditedForm(page, register);
  await expect.poll(() => submitted).toEqual([
    { email: 'wallet@example.test', password: 'synthetic-password' },
    { email: 'retry@example.test', password: 'retry-password' },
  ]);
});
