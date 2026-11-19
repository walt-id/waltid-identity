const { readFileSync } = require('node:fs');
const path = require('node:path');
const fixtureDirectory = path.resolve(__dirname, '../../../waltid-wallet-demo-test-fixtures/resources/files');
const artwork = require(path.join(fixtureDirectory, 'credential-artwork.json'));
const statusCases = require(path.join(fixtureDirectory, 'credential-status.json')).values;

// Synthetic HTTP boundary, using the same artwork and producer assessments as mobile.
// These status values do not describe credentials issued by the public PID/EMVCo/KYC issuer.
async function createWalletFixture(page, options = {}) {
  const cards = options.cards || artwork.cards;
  const records = cards.map(card => ({
    id: card.id, label: card.title, format: card.format || 'jwt_vc_json', issuer: 'did:example:layout-issuer',
    subject: 'did:example:holder', addedAt: card.addedAt || '2026-11-12T09:00:00Z',
  }));
  const details = Object.fromEntries(records.map((record, i) => [record.id, {
    ...record,
    metadata: { issuerDisplay: [{ name: 'Layout fixture' }], credentialDisplay: [{
      name: record.label, background_color: cards[i].background || '#234D70', text_color: '#FFFFFF',
      ...(cards[i].image ? { background_image: { uri: `https://wallet-visual.example/artwork/${cards[i].image}` } } : {}),
    }] },
    credential: { ...record, credentialData: cards[i].credentialData || {
      type: ['VerifiableCredential', cards[i].title.split(/\W+/).map(word => word[0].toUpperCase() + word.slice(1)).join('')],
      credentialSubject: { given_name: 'Alex', family_name: 'Example', access: record.label },
    } },
  }]));
  const statuses = new Map(cards.map(card => [card.id, card.status || 'clear']));
  const persistedStatuses = new Map(statuses);
  const outcomes = { 'art-pid': 'UNCHANGED', 'art-emvco': 'UNCHANGED', ...options.outcomes };
  const requests = [], refreshes = [], submissions = [], unexpected = [], errors = [], images = new Set();
  let context, sequence = 0, expired = false, refreshGate, contextRejections = 0;
  const reference = id => ({ storeIndex: 0, credentialId: id });
  const state = (id, scoped = true) => statusCases[(scoped || !options.noStore ? statuses : persistedStatuses).get(id)];
  const snapshot = scoped => ({
    evaluatedAt: '2026-11-12T10:00:00Z', refreshing: [], nextRefreshAt: null,
    credentials: records.map(record => ({ reference: reference(record.id), ...state(record.id, scoped) })),
    ...(scoped && context ? { statusContext: context } : {}),
  });
  await page.addInitScript(() => {
    localStorage.setItem('waltid.wallet2.baseUrl', location.origin);
    localStorage.setItem('waltid.wallet2.token', 'synthetic-browser-token');
    localStorage.setItem('waltid.wallet2.walletId', 'synthetic-wallet');
    localStorage.setItem('waltid.wallet2.walletTargets', JSON.stringify(['synthetic-wallet']));
  });
  page.on('pageerror', error => errors.push(error.message));
  await page.route('https://wallet-visual.example/artwork/*', async route => {
    const file = path.basename(new URL(route.request().url()).pathname);
    if (!artwork.artwork.some(item => item.file === file)) {
      unexpected.push(`artwork ${file}`); return route.abort();
    }
    await route.fulfill({ contentType: 'image/png', body: readFileSync(path.join(fixtureDirectory, file)),
      headers: { 'Access-Control-Allow-Origin': '*' } });
    images.add(file);
  });
  await page.route(url => url.pathname === '/wallet' || url.pathname.startsWith('/wallet/'), async route => {
    const url = new URL(route.request().url());
    const fullPath = url.pathname;
    const method = route.request().method();
    const suffix = fullPath.replace('/wallet/synthetic-wallet', '');
    const body = method === 'POST' ? route.request().postDataJSON() : null;
    requests.push({ method, path: fullPath, query: url.search, body });
    const changed = () => { contextRejections++; return route.fulfill({ status: 409, json: { code: 'credential_status_review_changed' } }); };
    if (suffix === '/credential-status' && method === 'GET') {
      if (options.oldServer) return route.fulfill({ status: 404, json: {} });
      if (url.searchParams.has('statusContext') && (expired || url.searchParams.get('statusContext') !== context?.id)) return changed();
      return route.fulfill({ json: snapshot(url.searchParams.has('statusContext')) });
    }
    if (suffix === '/credential-status/context' && method === 'POST') {
      if (options.oldServer) return route.fulfill({ status: 404, json: {} });
      context = { id: `visual-status-context-${++sequence}`, expiresAt: '2099-01-01T00:00:00Z' }; expired = false;
      return route.fulfill({ status: 201, json: snapshot(true) });
    }
    if (suffix.startsWith('/credential-status/context/') && method === 'DELETE') return route.fulfill({ status: 204 });
    if (suffix === '/credential-status/refresh' && method === 'POST' && !options.oldServer) {
      refreshes.push(body);
      if (expired || body.statusContext !== context?.id) return changed();
      if (refreshGate && body.mode === 'FORCE') { const gate = refreshGate; refreshGate = null; await gate; }
      const selected = body.credentials || records.map(record => reference(record.id));
      if (body.mode === 'FORCE' && options.refreshTo) {
        for (const item of selected) statuses.set(item.credentialId, options.refreshTo);
      }
      return route.fulfill({ json: selected.map(item => ({ reference: item,
        outcome: body.mode === 'FORCE' ? (outcomes[item.credentialId] || 'COMPLETED') : 'UNCHANGED',
        ...state(item.credentialId), statusContext: context,
        hasCheckErrors: ['revoked_partial', 'unverified'].includes(statuses.get(item.credentialId)),
      })) });
    }
    if (suffix === '/credentials/present/preview' && method === 'POST') {
      const selected = options.reviewIds || [records[0].id, records.at(-1).id];
      return route.fulfill({ json: { valid: true, keyId: 'synthetic-key', clientId: 'https://verifier.example',
        verifier: { name: 'Layout verifier' }, responseUri: 'https://verifier.example/response',
        credentialOptions: selected.map((id, i) => ({ queryId: `query-${i}`, credentialId: id, multiple: false,
          ...records.find(record => record.id === id), credentialData: details[id].credential.credentialData,
          disclosures: [{ path: 'given_name', name: 'Given name', value: 'Alex', selectivelyDisclosable: false,
            required: true, selectable: false, requested: true }],
        })),
        credentialRequirements: [{ options: [selected.map((_, i) => `query-${i}`)] }],
        transactionData: options.transactionData || [],
      } });
    }
    if (suffix.endsWith('/status-reviewed') && method === 'POST') {
      submissions.push({ path: suffix, body });
      if (body.statusContext != null && (expired || body.statusContext !== context?.id)) return changed();
      return route.fulfill({ json: suffix.includes('build-vp-token') ? { vpToken: 'synthetic-vp-token' } : { transmission_success: true } });
    }
    if (suffix.endsWith('/set-default') && method === 'PUT') return route.fulfill({ status: 204 });
    if (method === 'GET') {
      if (fullPath === '/wallet') return route.fulfill({ json: ['synthetic-wallet'] });
      if (suffix === '') return route.fulfill({ json: { walletId: 'synthetic-wallet', defaultKeyId: 'synthetic-key', defaultDidId: 'did:example:holder' } });
      if (suffix === '/dids') return route.fulfill({ json: [{ did: 'did:example:holder' }] });
      if (suffix === '/credentials') return route.fulfill({ json: records });
      if (suffix === '/credentials/receive/deferred') return route.fulfill({ json: [] });
      const id = suffix.startsWith('/credentials/') ? decodeURIComponent(suffix.slice('/credentials/'.length)) : null;
      if (id && details[id]) return route.fulfill({ json: details[id] });
    }
    unexpected.push(`${method} ${fullPath}`);
    return route.fulfill({ status: 404, json: {} });
  });
  return { records, requests, refreshes, submissions, unexpected, errors, images, reference,
    get context() { return context; },
    get contextRejections() { return contextRejections; },
    expireContext() { expired = true; },
    holdNextRefresh() { let release; refreshGate = new Promise(resolve => { release = resolve; }); return release; },
  };
}

module.exports = { createWalletFixture, statusCases };
