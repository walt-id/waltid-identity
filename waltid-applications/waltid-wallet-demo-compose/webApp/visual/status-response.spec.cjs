const { createServer } = require('node:http');
const { test, expect } = require('@playwright/test');
const { readStatusResponse } = require('./status-response.cjs');

for (const [name, method, mode, transfer, result] of [
  ['completed poll', 'GET', undefined, 'complete', 'projection'],
  ['completed manual refresh', 'POST', 'FORCE', 'complete', 'projection'],
  ['canceled poll', 'GET', undefined, 'abort', 'cancellation'],
  ['canceled automatic refresh', 'POST', 'IF_DUE', 'abort', 'cancellation'],
  ['canceled manual refresh', 'POST', 'FORCE', 'abort', 'error'],
  ['disconnected poll', 'GET', undefined, 'disconnect', 'error'],
  ['malformed completed poll', 'GET', undefined, 'malformed', 'error'],
]) {
  test(`status evidence: ${name}`, async ({ page }) => {
    let pending;
    const server = createServer((request, response) => {
      if (request.url === '/') return response.end('<!doctype html><title>Status collector test</title>');
      if (!request.url.startsWith('/wallet/')) return response.writeHead(404).end();
      pending = response;
      response.writeHead(200, { 'Content-Type': 'application/json' });
      response.write('{"state":');
      if (transfer === 'complete') response.end('"checked"}');
      if (transfer === 'malformed') response.end();
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const origin = `http://127.0.0.1:${server.address().port}`;
    const path = `/wallet/test/credential-status${method === 'POST' ? '/refresh' : ''}`;
    try {
      await page.goto(origin);
      const received = page.waitForResponse(`${origin}${path}`);
      await page.evaluate(({ url, method, mode }) => {
        window.transfer = new AbortController();
        fetch(url, { method, body: mode ? JSON.stringify({ mode }) : undefined, signal: window.transfer.signal })
          .then(response => response.text()).catch(() => {});
      }, { url: `${origin}${path}`, method, mode });
      const response = await received;
      const observed = readStatusResponse(response, { method, path, mode })
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
    } finally {
      server.closeAllConnections();
      await new Promise(resolve => server.close(resolve));
    }
  });
}
