const { test, expect } = require('@playwright/test');
const catalogue = require('./catalogue.json');

const { clickControl, fillControl, waitForSettledImage, submitEditedForm } = require('./browser-controls.cjs');
const { createWalletFixture } = require('./wallet-fixture.cjs');

for (const scenario of catalogue.states.filter(state => state.id.startsWith('account.'))) {
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
      const fixture = await createWalletFixture(page, { cards: [{
        id: 'synthetic-date-credential', title: 'Date display credential', status: 'not_provided', addedAt: '2026-10-25T00:35:00Z',
        credentialData: { iat: 1792888200, exp: 1792891800,
          validFrom: '2026-10-25T00:30:00.123456789Z', credentialSubject: { birth_date: '1990-05-21' } },
      }] });
      await page.goto('/');
      await clickControl(page, page.getByRole('button', { name: /^Date display credential(,|$)/ }));
      const birthDate = page.getByText('1990-05-21', { exact: true });
      await expect(birthDate).toHaveCount(1);
      await page.mouse.move(scenario.width / 2, scenario.height / 2);
      await page.mouse.wheel(0, scenario.height);
      await waitForSettledImage(page);
      await expect(birthDate).toBeVisible();
      await clickControl(page, page.getByRole('button', { name: 'Technical details', exact: true }));
      for (const value of [
        '25 Oct 2026, 02:30 \u2068(Budapest, CEST)\u2069',
        '25 Oct 2026, 02:30 \u2068(Budapest, CET)\u2069',
        '25 Oct 2026, 02:30:00.123456789 \u2068(Budapest, CEST)\u2069',
        '25 Oct 2026, 02:35 \u2068(Budapest, CEST)\u2069',
      ]) await expect(page.getByText(value, { exact: true })).toBeVisible();
      await waitForSettledImage(page);
      expect(fixture.unexpected).toEqual([]); expect(fixture.errors).toEqual([]);
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
