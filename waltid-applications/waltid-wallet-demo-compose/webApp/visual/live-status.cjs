// Explicit real-backend journey; excluded from the synthetic *.spec.cjs comparison lane.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const { chromium, expect } = require('@playwright/test');
const { clickControl, waitForSettledImage, revealReviewControl } = require('./browser-controls.cjs');

const required = name => {
  const value = process.env[name];
  if (!value) throw Error(`Set ${name} explicitly for the isolated real-backend journey`);
  return value;
};
const localService = name => {
  const value = new URL(required(name));
  if (!['localhost', '127.0.0.1', '[::1]', 'host.docker.internal'].includes(value.hostname)) {
    throw Error(`${name} must use a local test service`);
  }
  return value.href.replace(/\/$/, '');
};
const git = (directory, ...args) => execFileSync('git', ['-C', directory, ...args], { encoding: 'utf8' }).trim();
const digest = value => crypto.createHash('sha256').update(value).digest('hex');
const root = git(__dirname, 'rev-parse', '--show-toplevel');
const source = git(root, 'rev-parse', 'HEAD');
const clean = () => {
  if (git(root, 'status', '--porcelain')) throw Error('Real evidence requires a clean Identity checkout');
  if (git(root, 'rev-parse', 'HEAD') !== source) throw Error('Source changed during the journey');
};

async function json(url, body) {
  const response = await fetch(url, {
    method: body ? 'POST' : 'GET', headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(45000),
  });
  if (!response.ok) throw Error(`Backend ${response.status} at ${new URL(url).pathname}`);
  return response.json();
}

async function run() {
  clean();
  const server = localService('WALLET_API2_E2E_BASE_URL');
  const control = localService('WALLET_STATUS_CONTROL_URL');
  const app = localService('WALLET_BROWSER_APP_URL');
  const output = path.resolve(required('WALLET_BROWSER_EVIDENCE_DIR'));
  const backend = JSON.parse(fs.readFileSync(required('WALLET_API2_E2E_PRODUCER'), 'utf8'));
  fs.mkdirSync(output); // Never overwrite a previous journey or its diagnostics.
  const wallet = await json(`${server}/wallet`, {});
  const browser = await chromium.launch({
    headless: true, ...(process.env.WALLET_BROWSER_EXECUTABLE ? { executablePath: process.env.WALLET_BROWSER_EXECUTABLE } : {}),
  });
  const context = await browser.newContext({
    viewport: { width: 1280, height: 1000 }, locale: 'en-US', timezoneId: 'UTC',
    ...(process.env.WALLET_BROWSER_USER_AGENT ? { userAgent: process.env.WALLET_BROWSER_USER_AGENT } : {}),
  });
  await context.addInitScript(({ walletId, server }) => {
    // The isolated loopback service deliberately has auth disabled; no account token is used.
    localStorage.setItem('waltid.wallet2.baseUrl', server);
    localStorage.setItem('waltid.wallet2.token', 'local-unauthenticated-test-session');
    localStorage.setItem('waltid.wallet2.walletId', walletId);
    localStorage.setItem('waltid.wallet2.walletTargets', JSON.stringify([walletId]));
  }, { walletId: wallet.walletId, server });
  const page = await context.newPage();
  const requests = [], errors = [], frames = [], controls = new Map(), artworks = new Map();
  const observations = new Set();
  let requiredArtwork = [];
  let phase = 'bootstrap', admittedAt = Date.now(), verdict;
  page.on('pageerror', error => errors.push(error.message));
  async function observeResponse(response) {
    const url = new URL(response.url());
    if (response.headers()['content-type']?.startsWith('image/png')) {
      try { artworks.set(response.url(), digest(await response.body())); }
      catch { errors.push(`Unreadable artwork at ${url.pathname}`); }
    }
    if (!url.pathname.startsWith('/wallet')) return;
    const body = response.request().postData();
    const item = { method: response.request().method(), path: url.pathname.replace(wallet.walletId, ':wallet'), status: response.status() };
    if (body) {
      item.requestDigest = digest(body);
      try { item.mode = JSON.parse(body).mode; } catch { /* Not every request body is JSON. */ }
    }
    try {
      if (url.pathname.includes('/credential-status')) {
        item.projection = await response.json();
        if (url.pathname.endsWith('/refresh') && Array.isArray(item.projection) &&
          item.projection.some(result => result.outcome !== 'DEFERRED')) admittedAt = Date.now();
      }
    } catch (error) {
      errors.push(`Unreadable status response at ${item.path}: ${error.message}`);
    } finally { requests.push(item); }
  }
  page.on('response', response => {
    const observation = observeResponse(response);
    observations.add(observation);
    observation.finally(() => observations.delete(observation));
  });
  const button = name => page.getByRole('button', { name, exact: true });
  const headline = name => page.getByText(name, { exact: true }).first();
  const refreshes = () => requests.filter(request => request.method === 'POST' && request.path.endsWith('/credential-status/refresh'));
  const click = async locator => {
    await expect(locator).toBeVisible({ timeout: 30000 });
    await waitForSettledImage(page, await locator.boundingBox());
    await clickControl(page, locator);
  };
  async function rememberControl(name, locator) {
    await page.mouse.move(5, 350);
    await waitForSettledImage(page, await locator.boundingBox());
    const bounds = await locator.boundingBox();
    const box = { x: Math.floor(bounds.x), y: Math.floor(bounds.y), width: Math.ceil(bounds.width), height: Math.ceil(bounds.height) };
    controls.set(name, { box, pixels: await page.screenshot({ clip: box }) });
  }
  async function expectRemembered(name, timeout = 5000) {
    // The pinned renderer can retain a dismissed popup's ARIA mirror. Check the actual
    // undimmed control at its previously measured location before using that location.
    const { box, pixels } = controls.get(name);
    await page.mouse.move(5, 350);
    await expect.poll(async () => (await page.screenshot({ clip: box })).equals(pixels), { timeout }).toBe(true);
    return box;
  }
  async function clickRemembered(name) {
    const box = await expectRemembered(name);
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  }
  async function capture(id, visible) {
    await expect(visible).toBeVisible();
    await waitForSettledImage(page);
    await expect(visible).toBeVisible();
    const bytes = await page.screenshot({ path: path.join(output, `${id}.png`) });
    frames.push({ id, sha256: digest(bytes), width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) });
  }
  async function ready() {
    await expect(button('Scan or paste a link')).toBeVisible({ timeout: 30000 });
    await waitForSettledImage(page);
    await rememberControl('scan', button('Scan or paste a link'));
    await rememberControl('settings', button('Settings'));
  }
  async function link(value) {
    await clickRemembered('scan');
    await click(button('Enter a link'));
    // The scanner focuses a blinking backing editor; do not use it as a settled-image gate.
    await clickControl(page, page.getByRole('textbox', { name: 'Credential offer or request', exact: true }));
    await page.keyboard.insertText(value);
    await page.keyboard.press('Tab');
    await click(button('Continue'));
  }
  async function settings() {
    await clickRemembered('settings');
    await click(page.getByRole('button', { name: /^Credential status/ }));
    await expect(button('Automatic checks')).toBeVisible();
  }
  async function leaveSettings() {
    await click(button('Back')); await click(button('Back'));
  }
  async function cooldown() {
    // Wallet API 2 admits status refreshes once per wallet per 30 seconds. This is
    // a protocol admission wait, not a capture delay or an automatic refresh retry.
    const remaining = admittedAt + 30000 - Date.now();
    if (remaining > 0) await new Promise(resolve => setTimeout(resolve, remaining + 100));
  }
  async function issue(url, label) {
    phase = `issue ${label}`;
    await link(url); await click(button('Accept'));
    await expect(button('Done')).toBeVisible({ timeout: 45000 });
    await click(button('Done'));
  }
  async function openCard(title) {
    const card = page.getByRole('button', { name: new RegExp(`^${title}(,|$)`) });
    await expect(card).toBeVisible(); await waitForSettledImage(page);
    const box = await card.boundingBox();
    await page.mouse.click(box.x + box.width / 2, box.y + 20); // Exposed stack band, measured from the real card.
    await expect(button('Close')).toBeVisible();
  }
  async function refresh(title) {
    await cooldown();
    const before = refreshes().length;
    await click(button('Refresh status'));
    await expect.poll(() => refreshes().length, { timeout: 30000 }).toBe(before + 1);
    const projection = refreshes().at(-1).projection;
    expect(Array.isArray(projection), 'Refresh response must contain producer results').toBe(true);
    expect(projection.some(result => result.outcome !== 'DEFERRED')).toBe(true);
    await expect(headline(title)).toBeVisible({ timeout: 30000 });
  }
  const setStatus = (reference, value) => json(`${control}/status`, { purpose: reference.purpose, index: reference.index, value });
  const verifier = 'https://verifier2.demo.walt.id';
  try {
    await page.goto(app); await ready();
    await settings(); await click(button('Automatic checks')); await leaveSettings();
    const automaticBefore = refreshes().length;
    const eventOffer = await json(`${control}/offers`, { configuration: 'event_pass' });
    await issue(eventOffer.credentialOffer, 'conference');
    const pidOffer = await json('https://issuer2.demo.walt.id/issuer2/credential-offers', { profileId: 'eudiPidSdJwt', authMethod: 'PRE_AUTHORIZED' });
    await issue(pidOffer.credentialOffer, 'public PID');
    const accessOffer = await json(`${control}/offers`, { configuration: 'staff_access' });
    await issue(accessOffer.credentialOffer, 'workplace');
    expect(refreshes().length).toBe(automaticBefore);
    const credentials = await json(`${server}/wallet/${wallet.walletId}/credentials`);
    expect(credentials).toHaveLength(3);
    for (const credential of credentials) {
      const detail = await json(`${server}/wallet/${wallet.walletId}/credentials/${credential.id}`);
      const image = detail.metadata?.credentialDisplay?.[0]?.background_image?.uri;
      expect(image).toBeTruthy();
      requiredArtwork.push(image);
    }
    // A reload also verifies that the actual server retains all three received credentials.
    // Browser demo preferences are runtime-owned, so explicitly restore manual mode afterwards.
    // Finish inspecting response bodies before navigation invalidates Chromium's body handles.
    await Promise.all([...observations]);
    await page.reload(); await ready();
    await expect.poll(() => requiredArtwork.every(uri => artworks.has(uri))).toBe(true);
    phase = 'settings'; await settings(); await click(button('Automatic checks'));
    await capture('automatic-disabled', button('Automatic checks'));
    await cooldown();
    const batch = refreshes().length;
    await click(button('Check statuses'));
    await expect.poll(() => refreshes().length).toBe(batch + 1);
    await capture('settings-manual', headline('Processed'));
    await click(button('How checks work'));
    await capture('settings-help', headline('Recipients verify credential status independently.'));
    await leaveSettings();
    await capture('home-clear', page.getByRole('button', { name: /^PID(,|$)/ }));
    const state = await json(`${control}/state`);
    // The fixture contract runs these journeys serially. Each newly issued credential has
    // its own monotonic index; irreversible revocation is never reset to manufacture a state.
    const event = state.issued.filter(item => item.configuration === 'event_pass').at(-1);
    const access = state.issued.filter(item => item.configuration === 'staff_access').at(-1);
    expect(event).toBeTruthy(); expect(access).toBeTruthy();
    phase = 'suspension'; await setStatus(event, 1); await openCard('Conference pass');
    await refresh('Suspended'); await capture('details-suspended', headline('Suspended'));
    await setStatus(event, 0); await refresh('Status clear'); await capture('details-clear', headline('Status clear'));
    await click(button('Close'));
    phase = 'revocation'; await setStatus(access, 1); await openCard('Workplace access');
    await refresh('Revoked'); await capture('details-revoked', headline('Revoked')); await click(button('Close'));
    await capture('home-adverse', page.getByRole('button', { name: /^Workplace access,/ }));
    phase = 'sharing';
    const sessionId = crypto.randomUUID();
    const session = await json(`${verifier}/verification-session/create`, { flow_type: 'cross_device', core_flow: {
      signed_request: false, sessionId, clientId: `redirect_uri:${verifier}/verification-session/${sessionId}/response`,
      dcql_query: { credentials: [
        { id: 'pid', format: 'dc+sd-jwt', meta: { vct_values: ['urn:eudi:pid:1'] } },
        { id: 'workplace', format: 'jwt_vc_json', meta: { type_values: [['VerifiableCredential', 'WorkplaceAccessCredential']] } },
      ] },
    } });
    expect(session.sessionId).toBe(sessionId);
    await link(session.fullAuthorizationRequestUrl);
    await expect(button('Share')).toBeVisible({ timeout: 30000 });
    await capture('sharing', button('Share'));
    const status = page.getByRole('button', { name: /revoked/i });
    await click(status); await revealReviewControl(page, button('Refresh status'));
    await refresh('Revoked'); await capture('sharing-refreshed', headline('Last checked'));
    await revealReviewControl(page, status); await click(status);
    await rememberControl('share', button('Share')); await click(button('Share'));
    await capture('sharing-confirmation', headline('Share with status issues?'));
    await click(button('Back to review')); await clickRemembered('share');
    await click(button('Share anyway'));
    await expect.poll(() => requests.filter(item => item.path.endsWith('/present/send-response/status-reviewed') && item.status === 200).length,
      { timeout: 30000 }).toBe(1);
    await expect.poll(async () => {
      verdict = await json(`${verifier}/verification-session/${sessionId}/info`); return verdict.status;
    }, { timeout: 30000 }).toBe('SUCCESSFUL');
    // Successful receipts auto-dismiss after five seconds plus the sheet animation. The
    // renderer can retain their ARIA mirror, so verify the actual undimmed Home control.
    await expectRemembered('scan', 15000);
    await Promise.all([...observations]);
    expect(errors).toEqual([]);
    expect(frames).toHaveLength(11);
    clean();
  } catch (error) {
    await page.screenshot({ path: path.join(output, 'error.png') });
    fs.writeFileSync(path.join(output, 'error-aria.txt'), await page.locator('body').ariaSnapshot());
    throw Error(`${phase}: ${error.message}`);
  } finally {
    fs.writeFileSync(path.join(output, 'evidence.json'), JSON.stringify({
      sourceRevision: source, backend, phase, browser: browser.version(), platform: process.platform,
      userAgent: process.env.WALLET_BROWSER_USER_AGENT || 'Playwright default',
      auth: 'isolated loopback service with auth disabled', frames, requests, errors,
      artworkDigests: requiredArtwork.map(uri => ({ uriDigest: digest(uri), pngDigest: artworks.get(uri) })),
      verifier: verdict ? { status: verdict.status, policyResults: verdict.policy_results } : null,
    }, null, 2));
    await browser.close();
  }
}

run().catch(error => { console.error(error.message); process.exitCode = 1; });
