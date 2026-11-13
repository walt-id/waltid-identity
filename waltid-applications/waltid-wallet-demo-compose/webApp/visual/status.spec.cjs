const { test, expect } = require('@playwright/test');
const catalogue = require('./catalogue.json');
const { createWalletFixture, statusCases } = require('./wallet-fixture.cjs');
const { clickControl, waitForSettledImage, revealReviewControl: reveal } = require('./browser-controls.cjs');

async function openCard(page, title) {
  const card = page.getByRole('button', { name: new RegExp(`^${title}(,|$)`) });
  await expect(card).toBeVisible();
  const box = await card.boundingBox();
  // A stacked card's center can be covered by another credential. Use its exposed top band.
  await page.mouse.click(box.x + box.width / 2, box.y + Math.min(24, box.height / 2));
}

async function openSharing(page, fixture) {
  await clickControl(page, page.getByRole('button', { name: 'Scan or paste a link', exact: true }));
  await waitForSettledImage(page);
  await clickControl(page, page.getByRole('button', { name: 'Enter a link', exact: true }));
  const requestUrl = 'openid4vp://authorize?client_id=https%3A%2F%2Fverifier.example';
  await clickControl(page, page.getByRole('textbox', { name: 'Credential offer or request', exact: true }));
  await page.keyboard.insertText(requestUrl);
  await page.keyboard.press('Tab');
  const proceed = page.getByRole('button', { name: 'Continue', exact: true });
  await waitForSettledImage(page, await proceed.boundingBox());
  await clickControl(page, proceed);
  await expect.poll(() => fixture.requests.find(request => request.path.endsWith('/present/preview'))?.body?.requestUrl).toBe(requestUrl);
  await expect(page.getByRole('button', { name: 'Share', exact: true })).toBeVisible();
  await expect(page.getByText('Revoked', { exact: true })).toBeVisible();
}


function expectTransport(fixture) {
  expect(fixture.unexpected).toEqual([]);
  expect(fixture.errors).toEqual([]);
}

for (const scenario of catalogue.states.filter(state => state.id.includes('.status.'))) {
  test.describe(scenario.id, () => {
    test.use({ locale: scenario.locale || 'en-US', timezoneId: scenario.timezoneId || 'UTC' });
    test(scenario.id, async ({ page }, testInfo) => {
      await page.setViewportSize({ width: scenario.width, height: scenario.height });
      testInfo.annotations.push({ type: 'requirements', description: scenario.requirements.join(', ') });
      const detail = scenario.state.startsWith('status-');
      const oldServer = scenario.state === 'status-unsupported';
      const fixture = await createWalletFixture(page, detail ? {
        cards: [{ id: 'status-review', title: 'Status review credential', status: 'clear', background: '#234D70' }],
        oldServer, refreshTo: 'revoked_partial', noStore: true,
      } : scenario.state === 'sharing-transaction' ? {
        transactionData: [{ type: 'order_confirmation', credentialQueryIds: ['query-0', 'query-1'],
          rawJson: { type: 'order_confirmation', amount: '11.56', currency: 'EUR', payee: { name: 'Example Bookshop' } },
          details: { amount: '11.56', currency: 'EUR', payee: { name: 'Example Bookshop' } } }],
      } : {});
      await page.goto('/');
      if (scenario.state === 'home') {
        await expect.poll(() => fixture.images.size).toBe(3);
        const card = page.getByRole('button', { name: /^PID(,|$)/ });
        await expect(card).toBeVisible();
        const box = await card.boundingBox();
        expect(box.width).toBeLessThanOrEqual(360);
        expect(Math.abs(box.x + box.width / 2 - scenario.width / 2)).toBeLessThanOrEqual(1);
        await expect(page.getByText('Status clear', { exact: true })).toHaveCount(0);
      } else if (detail) {
        await openCard(page, 'Status review credential');
        await reveal(page, page.getByText(oldServer ? 'Status unavailable' : 'Status clear', { exact: true }));
        if (oldServer) {
          await expect(page.getByRole('button', { name: 'Refresh status', exact: true })).toHaveCount(0);
          await expect(page.getByText('This server does not expose credential status checks. The credential can still be shared, and the recipient performs its own checks.', { exact: true })).toBeVisible();
          expect(fixture.refreshes).toEqual([]);
        } else {
          await expect(page.getByRole('button', { name: 'Details', exact: true })).toHaveCount(0);
          if (scenario.state === 'status-supported') {
            await clickControl(page, page.getByRole('button', { name: 'Refresh status', exact: true }));
            await expect(page.getByText('Last known: revoked', { exact: true })).toBeVisible();
            await expect(page.getByText('Partly checked', { exact: true })).toBeVisible();
            const notice = page.getByText('Status changed: Status review credential. Open the marked cards for details.', { exact: true });
            await expect(notice).toBeVisible();
            await expect(notice).toHaveCount(0);
            const disclosure = page.getByRole('button', { name: 'Details', exact: true });
            await clickControl(page, disclosure);
            await reveal(page, page.getByText('Latest attempt', { exact: true }));
            const force = fixture.refreshes.filter(request => request.mode === 'FORCE');
            expect(force).toEqual([{ credentials: [fixture.reference('status-review')], mode: 'FORCE', statusContext: fixture.context.id }]);
            // Resizing preserves the current credential and its expanded facts.
            await page.setViewportSize({ width: 760, height: scenario.height });
            await expect(page.getByText('Latest attempt', { exact: true })).toHaveCount(1);
            await page.setViewportSize({ width: scenario.width, height: scenario.height });
            await reveal(page, page.getByText('Latest attempt', { exact: true }));
          }
        }
      } else if (scenario.state === 'settings-help') {
        await clickControl(page, page.getByRole('button', { name: 'Settings', exact: true }));
        await clickControl(page, page.getByRole('button', { name: /^Credential status/ }));
        await expect(page.getByText(/^While this wallet is open and signed in\./)).toBeVisible();
        await clickControl(page, page.getByRole('button', { name: 'Check statuses', exact: true }));
        await expect(page.getByText('Processed', { exact: true })).toBeVisible();
        const requestsBefore = fixture.refreshes.length;
        const help = page.getByRole('button', { name: 'How checks work', exact: true });
        await clickControl(page, help);
        await expect(page.getByText('Recipients verify credential status independently.', { exact: true })).toBeVisible();
        await clickControl(page, help);
        await expect(page.getByText('Recipients verify credential status independently.', { exact: true })).toHaveCount(0);
        await page.keyboard.press('Space');
        await expect(page.getByText('Recipients verify credential status independently.', { exact: true })).toBeVisible();
        expect(fixture.refreshes.length).toBe(requestsBefore);
      } else {
        await openSharing(page, fixture);
        if (scenario.state === 'sharing-transaction') {
          await expect(page.getByText('Example Bookshop', { exact: true })).toBeVisible();
          await expect(page.getByText('11.56', { exact: true })).toBeVisible();
          await expect(page.getByText('EUR', { exact: true })).toBeVisible();
        }
        const forcesBefore = fixture.refreshes.filter(request => request.mode === 'FORCE').length;
        if (scenario.state === 'sharing-expanded') {
          await clickControl(page, page.getByRole('button', { name: /Revoked/, exact: false }));
          await reveal(page, page.getByRole('button', { name: 'Refresh status', exact: true }));
        } else if (scenario.state === 'sharing-confirmation') {
          await clickControl(page, page.getByRole('button', { name: 'Share', exact: true }));
          await expect(page.getByText('Share with status issues?', { exact: true })).toBeVisible();
          await expect(page.getByRole('button', { name: 'Share anyway', exact: true })).toBeVisible();
          expect(fixture.submissions).toEqual([]);
        }
        expect(fixture.refreshes.filter(request => request.mode === 'FORCE').length).toBe(forcesBefore);
      }
      await waitForSettledImage(page);
      expectTransport(fixture);
      await expect(page).toHaveScreenshot(`${scenario.id}.png`, { animations: 'disabled', caret: 'hide' });
      await testInfo.attach(scenario.id, { body: await page.screenshot(), contentType: 'image/png' });
      if (scenario.state === 'status-supported') {
        const disclosure = page.getByRole('button', { name: 'Details', exact: true });
        await reveal(page, disclosure);
        await clickControl(page, disclosure);
        await expect(page.getByText('Latest attempt', { exact: true })).toHaveCount(0);
        await page.keyboard.press('Space');
        await expect(page.getByText('Latest attempt', { exact: true })).toHaveCount(1);
        await page.mouse.move(scenario.width / 2, scenario.height / 2);
        await page.mouse.wheel(0, -scenario.height * 4);
        await waitForSettledImage(page);
        const more = page.getByRole('button', { name: 'More', exact: true });
        const header = await more.boundingBox();
        await page.mouse.move(header.x + header.width / 2, header.y + header.height / 2);
        await waitForSettledImage(page);
        const top = Math.ceil(header.y + header.height + 8);
        const clip = { x: 0, y: top, width: scenario.width, height: scenario.height - top };
        const backdrop = await page.screenshot({ clip });
        await clickControl(page, more);
        await expect(page.getByText('Copy', { exact: true })).toBeVisible();
        await page.keyboard.press('Escape');
        await waitForSettledImage(page);
        await expect.poll(async () => (await page.screenshot({ clip })).equals(backdrop)).toBe(true);
        expectTransport(fixture);
      }
    });
  });
}

test('online review refresh renders before deliberate consent and uses its active context', async ({ page }) => {
  await page.setViewportSize({ width: 393, height: 1000 });
  const fixture = await createWalletFixture(page);
  await page.goto('/');
  await openSharing(page, fixture);
  const status = page.getByRole('button', { name: /Revoked/, exact: false });
  await clickControl(page, status);
  const refresh = page.getByRole('button', { name: 'Refresh status', exact: true });
  await reveal(page, refresh);
  const release = fixture.holdNextRefresh();
  await clickControl(page, refresh);
  const share = page.getByRole('button', { name: 'Share', exact: true });
  try {
    await expect.poll(() => fixture.refreshes.filter(request => request.mode === 'FORCE').length).toBe(1);
    await clickControl(page, status);
    await expect(page.getByText('Last checked', { exact: true })).toHaveCount(0);
    await clickControl(page, share);
    await expect(page.getByText('Share with status issues?', { exact: true })).toHaveCount(0);
    expect(fixture.submissions).toEqual([]);
  } finally { release(); }
  await expect(status).toBeVisible();
  await waitForSettledImage(page, await share.boundingBox());
  const shareBounds = await share.boundingBox();
  const viewport = page.viewportSize();
  const footerClip = { x: 0, y: viewport.height - 8, width: viewport.width, height: 8 };
  const footer = await page.screenshot({ clip: footerClip });
  await clickControl(page, share);
  await expect(page.getByText('Share with status issues?', { exact: true })).toBeVisible();
  await waitForSettledImage(page);
  await clickControl(page, page.getByRole('button', { name: 'Back to review', exact: true }));
  expect(fixture.submissions).toEqual([]);
  // The pinned renderer can retain a dismissed popup's ARIA mirror. Verify the
  // undimmed canvas and activate the unchanged, previously measured footer target.
  await expect.poll(async () => (await page.screenshot({ clip: footerClip })).equals(footer)).toBe(true);
  await page.mouse.click(shareBounds.x + shareBounds.width / 2, shareBounds.y + shareBounds.height / 2);
  await waitForSettledImage(page);
  await clickControl(page, page.getByRole('button', { name: 'Share anyway', exact: true }));
  await expect.poll(() => fixture.submissions.length).toBe(2);
  for (const submission of fixture.submissions) {
    expect(submission.body.statusContext).toBe(fixture.context.id);
    expect(submission.body.reviewedStatus).toEqual({
      'art-access': statusCases.revoked_current.presentation.advisoryRevision,
      'art-pid': statusCases.not_provided.presentation.advisoryRevision,
    });
  }
  expect(fixture.submissions[0].body.request.selectedCredentialOptions).toEqual([
    { queryId: 'query-0', credentialId: 'art-pid' }, { queryId: 'query-1', credentialId: 'art-access' },
  ]);
  expectTransport(fixture);
});

test('expired browser context closes pending consent even when issuer facts stay identical', async ({ page }, testInfo) => {
  await page.clock.install();
  const fixture = await createWalletFixture(page);
  await page.goto('/');
  await openSharing(page, fixture);
  await clickControl(page, page.getByRole('button', { name: /Revoked/, exact: false }));
  const refresh = page.getByRole('button', { name: 'Refresh status', exact: true });
  await reveal(page, refresh);
  await clickControl(page, refresh);
  await expect.poll(() => fixture.refreshes.filter(request => request.mode === 'FORCE').length).toBe(1);
  const share = page.getByRole('button', { name: 'Share', exact: true });
  await waitForSettledImage(page, await share.boundingBox());
  const viewport = page.viewportSize();
  const shareBounds = await share.boundingBox();
  const footerClip = { x: Math.round(shareBounds.x + shareBounds.width / 2), y: Math.round(shareBounds.y + 6), width: 1, height: 1 };
  await page.mouse.move(shareBounds.x + shareBounds.width / 2, shareBounds.y + shareBounds.height / 2);
  await waitForSettledImage(page, footerClip);
  const footer = await page.screenshot({ clip: footerClip });
  await clickControl(page, share);
  await expect(page.getByText('Share with status issues?', { exact: true })).toBeVisible();
  const requestsBefore = fixture.refreshes.length;
  fixture.expireContext();
  await page.clock.fastForward(30_000);
  await expect.poll(() => fixture.contextRejections).toBeGreaterThan(0);
  await page.mouse.move(shareBounds.x + shareBounds.width / 2, shareBounds.y + shareBounds.height / 2);
  await expect.poll(async () => (await page.screenshot({ clip: footerClip })).equals(footer)).toBe(true);
  await page.mouse.move(viewport.width / 2, viewport.height / 2);
  await waitForSettledImage(page);
  await testInfo.attach('expired-context-returned-review', { body: await page.screenshot(), contentType: 'image/png' });
  expect(fixture.submissions).toEqual([]);
  expect(fixture.refreshes.length).toBe(requestsBefore);
  // A newly rendered cold review can be consented to without an issuer check or
  // an extra failed submission merely to consume an internal invalidation latch.
  await page.mouse.click(shareBounds.x + shareBounds.width / 2, shareBounds.y + shareBounds.height / 2);
  await waitForSettledImage(page);
  await expect.poll(async () => (await page.screenshot({ clip: footerClip })).equals(footer)).toBe(false);
  await clickControl(page, page.getByRole('button', { name: 'Share anyway', exact: true }));
  await expect.poll(() => fixture.submissions.length).toBe(2);
  expect(fixture.refreshes.length).toBe(requestsBefore);
  expectTransport(fixture);
});
