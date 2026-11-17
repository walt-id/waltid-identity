const { createServer } = require('node:http');
const { test, expect } = require('@playwright/test');
const { readStatusResponse, retainResponseBodies } = require('./status-response.cjs');

for (const [name, method, mode, transfer, result, suffix, statusCheckOwner] of [
  ['completed poll', 'GET', undefined, 'complete', 'projection'],
  ['completed manual refresh', 'POST', 'FORCE', 'complete', 'projection'],
  ['manual refresh after reader closure', 'POST', 'FORCE', 'closed', 'projection'],
  ['canceled poll', 'GET', undefined, 'abort', 'cancellation'],
  ['canceled automatic refresh', 'POST', 'IF_DUE', 'abort', 'cancellation'],
  ['canceled manual refresh', 'POST', 'FORCE', 'abort', 'error'],
  ['canceled startup context', 'POST', undefined, 'abort', 'cancellation', '/context', 'automatic'],
  ['canceled manual context', 'POST', undefined, 'abort', 'error', '/context', 'manual'],
  ['canceled unowned context', 'POST', undefined, 'abort', 'error', '/context'],
  ['disconnected poll', 'GET', undefined, 'disconnect', 'error'],
  ['malformed completed poll', 'GET', undefined, 'malformed', 'error'],
]) {
  test(`status evidence: ${name}`, async ({ page }) => {
    let pending;
    const server = createServer((request, response) => {
      if (request.url === '/') return response.end('<!doctype html><title>Status collector test</title>');
      if (!request.url.startsWith('/wallet/')) return response.writeHead(404).end();
      pending = response;
      response.writeHead(suffix === '/context' ? 201 : 200, { 'Content-Type': 'application/json' });
      if (transfer === 'closed') return response.end('{"state":"checked"}');
      response.write('{"state":');
      if (transfer === 'complete') response.end('"checked"}');
      if (transfer === 'malformed') response.end();
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const origin = `http://127.0.0.1:${server.address().port}`;
    const path = `/wallet/test/credential-status${suffix ?? (method === 'POST' ? '/refresh' : '')}`;
    try {
      await retainResponseBodies(page);
      await page.goto(origin);
      for (let attempt = 0; attempt < (transfer === 'closed' ? 20 : 1); attempt++) {
        const received = page.waitForResponse(`${origin}${path}`);
        await page.evaluate(({ url, method, mode, transfer }) => {
          window.transfer = new AbortController();
          window.consumed = fetch(url, { method, body: mode ? JSON.stringify({ mode }) : undefined, signal: window.transfer.signal })
            .then(async response => {
              if (transfer !== 'closed') return response.text();
              const reader = response.body.getReader();
              const { value } = await reader.read();
              const result = JSON.parse(new TextDecoder().decode(value));
              await reader.cancel();
              window.transfer.abort();
              return result;
            }).catch(() => {});
        }, { url: `${origin}${path}`, method, mode, transfer });
        const response = await received;
        if (transfer === 'closed') expect(await page.evaluate(() => window.consumed)).toEqual({ state: 'checked' });
        const observed = readStatusResponse(response, { method, path, mode, statusCheckOwner })
          .then(value => ({ value }), error => ({ error }));
        if (transfer === 'abort') await page.evaluate(() => window.transfer.abort());
        if (transfer === 'disconnect') pending.destroy();
        const outcome = await observed;
        if (result === 'error') {
          expect(outcome.error).toBeTruthy();
          expect(outcome.value).toBeUndefined();
          if (transfer === 'abort') expect(response.request().failure()).toEqual({ errorText: 'net::ERR_ABORTED' });
          if (transfer === 'disconnect') expect(response.request().failure()?.errorText).toBeTruthy();
          if (transfer === 'malformed') expect(response.request().failure()).toBeNull();
        } else {
          expect(outcome.error).toBeUndefined();
          expect(outcome.value).toEqual(result === 'projection'
            ? { projection: { state: 'checked' } } : { cancellation: { errorText: 'net::ERR_ABORTED' } });
        }
      }
    } finally {
      server.closeAllConnections();
      await new Promise(resolve => server.close(resolve));
    }
  });
}
