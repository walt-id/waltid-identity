import './style.css';

const VERIFIER_PRESETS = {
  'open-source': {
    verifierBase: 'https://verifier2.portal.test.waltid.cloud'
  },
  enterprise: {
    verifierBase: '/verifier-api/v1/waltid.tenant1.verifier2/verifier2-service-api'
  }
} as const;
const DEFAULT_VERIFIER_PRESET = 'open-source';
const POLL_INTERVAL_MS = 10_000;
const MAX_POLL_ATTEMPTS = 60;
const SESSION_STORAGE_KEY = 'dc-api-test.sessionId';

type ExampleEntry = {
  title: string;
  payload: unknown;
};

type CreateResponse = {
  sessionId?: string;
};

type RuntimeConfig = {
  verifierBase: string;
  bearerToken: string;
};

type VerifierPresetKey = keyof typeof VERIFIER_PRESETS;

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => void init().catch(handleInitError));
} else {
  void init().catch(handleInitError);
}

async function init(): Promise<void> {
  const select = document.getElementById('example-select') as HTMLSelectElement | null;
  const payloadInput = document.getElementById('payload-input') as HTMLTextAreaElement | null;
  const callButton = document.getElementById('call-dc-api') as HTMLButtonElement | null;
  const reloadButton = document.getElementById('reload-examples') as HTMLButtonElement | null;
  const verifierPresetSelect = document.getElementById('verifier-preset') as HTMLSelectElement | null;
  const bearerTokenInput = document.getElementById('bearer-token') as HTMLInputElement | null;
  const statusEl = document.getElementById('status') as HTMLSpanElement | null;
  const logEl = document.getElementById('log-field') as HTMLPreElement | null;

  if (
    !select ||
    !payloadInput ||
    !callButton ||
    !reloadButton ||
    !verifierPresetSelect ||
    !bearerTokenInput ||
    !statusEl ||
    !logEl
  ) {
    throw new Error('Missing required DOM elements');
  }

  verifierPresetSelect.value = DEFAULT_VERIFIER_PRESET;

  let examples: ExampleEntry[] = [];

  const loadExamples = async (): Promise<void> => {
    reloadButton.disabled = true;
    callButton.disabled = true;
    try {
      setStatus(statusEl, 'Loading Swagger examples...');
      const config = getRuntimeConfig(verifierPresetSelect, bearerTokenInput);
      examples = await loadDcApiExamples(config);

      if (!examples.length) {
        setStatus(statusEl, 'No dc_api examples found in Swagger');
        return;
      }

      populateExampleSelect(select, examples);
      payloadInput.value = stringifyJson(examples[0].payload);
      setStatus(statusEl, `Ready (${examples.length} examples loaded)`);
      callButton.disabled = false;
    } finally {
      reloadButton.disabled = false;
    }
  };

  await loadExamples();

  reloadButton.addEventListener('click', () => {
    void loadExamples().catch((error) => {
      console.error('[dc-api-test] load examples failed', error);
      setStatus(statusEl, `Failed: ${toErrorMessage(error)}`);
    });
  });
  verifierPresetSelect.addEventListener('change', () => {
    void loadExamples().catch((error) => {
      console.error('[dc-api-test] load examples failed', error);
      setStatus(statusEl, `Failed: ${toErrorMessage(error)}`);
    });
  });

  select.addEventListener('change', () => {
    const selected = examples[select.selectedIndex];
    if (!selected) return;
    payloadInput.value = stringifyJson(selected.payload);
  });

  callButton.addEventListener('click', async () => {
    callButton.disabled = true;
    clearLog(logEl);
    appendLog(logEl, 'Starting DC API flow...');
    try {
      const config = getRuntimeConfig(verifierPresetSelect, bearerTokenInput);
      await runDcApiFlow(payloadInput.value, statusEl, logEl, config);
      setStatus(statusEl, 'Completed successfully.');
    } catch (error) {
      console.error('[dc-api-test] flow failed', error);
      appendLog(logEl, `Flow failed: ${toErrorMessage(error)}`);
      if (error instanceof HttpError) {
        appendPayloadLog(logEl, 'Failure payload', error.body);
      }
      setStatus(statusEl, 'Failed. See result log.');
    } finally {
      callButton.disabled = false;
    }
  });
}

async function loadDcApiExamples(config: RuntimeConfig): Promise<ExampleEntry[]> {
  const openApiUrls = getOpenApiCandidateUrls(config.verifierBase);
  let api: unknown | undefined;
  let lastError: unknown;

  for (let index = 0; index < openApiUrls.length; index += 1) {
    const openApiUrl = openApiUrls[index];
    try {
      api = await fetchJson(
        openApiUrl,
        {
          method: 'GET',
          headers: withAuthorization(
            {
              accept: 'application/json'
            },
            config.bearerToken
          )
        },
        `openapi.load#${index + 1}`
      );
      break;
    } catch (error) {
      lastError = error;
      console.warn('[dc-api-test] failed to load OpenAPI from candidate URL', {
        openApiUrl,
        error: toErrorMessage(error)
      });
    }
  }

  if (api === undefined) {
    throw lastError instanceof Error
      ? lastError
      : new Error('Failed to load OpenAPI from all candidate URLs');
  }

  const examplesObj = getCreateExamples(api);

  if (!examplesObj || typeof examplesObj !== 'object') {
    return [];
  }

  const examples: ExampleEntry[] = [];

  Object.entries(examplesObj).forEach(([title, raw]) => {
    if (!title.toLowerCase().includes('dc_api')) return;
    if (!raw || typeof raw !== 'object') return;
    const payload = (raw as { value?: unknown }).value;
    if (payload === undefined) return;
    examples.push({ title, payload });
  });

  return examples;
}

function getCreateExamples(api: unknown): Record<string, unknown> | null {
  const root = asRecord(api);
  const paths = asRecord(root?.paths);
  if (!paths) {
    return null;
  }

  const matchingPath = Object.entries(paths).find(([pathKey]) =>
    pathKey.endsWith('/verification-session/create')
  );
  if (!matchingPath) {
    return null;
  }

  const [, createPathRaw] = matchingPath;
  const createPath = asRecord(createPathRaw);
  const post = asRecord(createPath?.post);
  const requestBody = asRecord(post?.requestBody);
  const content = asRecord(requestBody?.content);
  const jsonContent = asRecord(content?.['application/json']);
  return asRecord(jsonContent?.examples);
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : null;
}

function populateExampleSelect(select: HTMLSelectElement, examples: ExampleEntry[]): void {
  select.innerHTML = '';

  examples.forEach((example) => {
    const option = document.createElement('option');
    option.value = example.title;
    option.textContent = example.title;
    select.appendChild(option);
  });

  select.selectedIndex = 0;
}

async function runDcApiFlow(
  payloadJson: string,
  statusEl: HTMLSpanElement,
  logEl: HTMLPreElement,
  config: RuntimeConfig
): Promise<void> {
  setStatus(statusEl, 'Parsing payload...');
  const createPayload = parseJson(payloadJson, 'payload-input');
  console.log('[dc-api-test] create payload', createPayload);
  appendPayloadLog(logEl, 'Create payload', createPayload);

  setStatus(statusEl, 'Creating verification session...');
  const createResponse = await fetchJson(
    buildUrl(config.verifierBase, '/verification-session/create'),
    {
      method: 'POST',
      headers: withAuthorization(
        {
          accept: 'application/json',
          'content-type': 'application/json'
        },
        config.bearerToken
      ),
      body: JSON.stringify(createPayload)
    },
    'session.create'
  );

  const sessionId = getSessionId(createResponse as CreateResponse);
  if (!sessionId) {
    throw new Error('No sessionId returned from create endpoint');
  }
  appendLog(logEl, `Session created: ${sessionId}`);

  sessionStorage.setItem(SESSION_STORAGE_KEY, sessionId);
  console.log('[dc-api-test] sessionId stored in sessionStorage', {
    key: SESSION_STORAGE_KEY,
    sessionId
  });

  const requestUrl = buildUrl(
    config.verifierBase,
    `/verification-session/${encodeURIComponent(sessionId)}/request`
  );
  setStatus(statusEl, 'Fetching DC API request...');
  const dcApiRequest = await fetchJsonWithFallback(
    [
      requestUrl,
      buildUrl(config.verifierBase, `/${encodeURIComponent(sessionId)}/request`)
    ],
    {
      method: 'GET',
      headers: withAuthorization(
        {
          accept: 'application/json'
        },
        config.bearerToken
      )
    },
    'session.request'
  );
  appendPayloadLog(logEl, 'DC API request payload', dcApiRequest);

  setStatus(statusEl, 'Calling navigator.credentials.get...');
  const dcApiResponse = await invokeDigitalCredentialsApi(dcApiRequest);
  appendPayloadLog(logEl, 'DC API wallet response', dcApiResponse);

  const responseUrl = buildUrl(
    config.verifierBase,
    `/verification-session/${encodeURIComponent(sessionId)}/response`
  );
  setStatus(statusEl, 'Posting wallet response...');
  const postResponse = await fetchAnyWithFallback(
    [
      responseUrl,
      buildUrl(config.verifierBase, `/${encodeURIComponent(sessionId)}/response`)
    ],
    {
      method: 'POST',
      headers: withAuthorization(
        {
          accept: 'application/json',
          'content-type': 'application/json'
        },
        config.bearerToken
      ),
      body: JSON.stringify(dcApiResponse)
    },
    'session.response'
  );
  appendLog(logEl, `Posted wallet response (HTTP ${postResponse.status})`);

  setStatus(statusEl, 'Polling verification-session info every 10 seconds...');
  const finalInfo = await pollInfo(sessionId, config, logEl);
  appendPayloadLog(logEl, 'Final result payload', finalInfo);
  console.log('[dc-api-test] final verification result', finalInfo);
}

async function pollInfo(
  sessionId: string,
  config: RuntimeConfig,
  logEl: HTMLPreElement
): Promise<unknown> {
  const infoUrl = buildUrl(config.verifierBase, `/verification-session/${encodeURIComponent(sessionId)}/info`);
  const fallbackInfoUrls = [
    infoUrl,
    buildUrl(config.verifierBase, `/${encodeURIComponent(sessionId)}/info`),
    buildUrl(
      config.verifierBase,
      `/verification-session/info?verification-session=${encodeURIComponent(sessionId)}`
    ),
    buildUrl(config.verifierBase, `/verification-session/info/${encodeURIComponent(sessionId)}`)
  ];

  for (let attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt += 1) {
    const info = await fetchJsonWithFallback(
      fallbackInfoUrls,
      {
        method: 'GET',
        headers: withAuthorization(
          {
            accept: 'application/json'
          },
          config.bearerToken
        )
      },
      `session.info#${attempt}`
    );

    const status = getInfoStatus(info);
    appendLog(logEl, `Poll #${attempt}: status=${status || 'UNKNOWN'}`);

    if (status === 'SUCCESSFUL') {
      appendLog(logEl, 'Verification status is SUCCESSFUL.');
      return info;
    }

    if (status !== 'IN_USE') {
      appendLog(logEl, `Verification failed with status ${status || 'UNKNOWN'}.`);
      appendPayloadLog(logEl, 'Failure payload', info);
      throw new Error(`Verification failed with status ${status || 'UNKNOWN'}`);
    }

    await sleep(POLL_INTERVAL_MS);
  }

  throw new Error(`Result not available after ${MAX_POLL_ATTEMPTS} polling attempts`);
}

function getInfoStatus(info: unknown): string | null {
  if (!info || typeof info !== 'object') return null;
  const status = (info as { status?: unknown }).status;
  if (typeof status !== 'string') return null;
  return status.toUpperCase();
}

async function invokeDigitalCredentialsApi(requestPayload: unknown): Promise<unknown> {
  const nav = navigator as Navigator & {
    credentials?: {
      get?: (options: unknown) => Promise<unknown>;
    };
  };

  if (typeof nav.credentials?.get !== 'function') {
    throw new Error('Digital Credentials API is unavailable in this browser (navigator.credentials.get missing).');
  }

  const dcRequestPayload =
    requestPayload != null &&
    typeof requestPayload === 'object' &&
    Object.prototype.hasOwnProperty.call(requestPayload, 'digital')
      ? requestPayload
      : {
          digital: {
            requests: [requestPayload]
          }
        };

  console.log('[dc-api-test] dc api request payload', dcRequestPayload);
  const result = await nav.credentials.get(dcRequestPayload);
  console.log('[dc-api-test] dc api response', result);

  if (result == null) {
    throw new Error('Digital Credentials API returned empty response');
  }

  return result;
}

async function fetchJson(url: string, init: RequestInit, label: string): Promise<unknown> {
  const response = await fetchAny(url, init, label);

  const text = await response.text();
  console.log(`[dc-api-test] ${label} raw response body`, text);

  if (!text) {
    return {};
  }

  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

async function fetchJsonWithFallback(urls: string[], init: RequestInit, label: string): Promise<unknown> {
  let lastError: unknown;

  for (let index = 0; index < urls.length; index += 1) {
    const url = urls[index];
    try {
      return await fetchJson(url, init, `${label}#${index + 1}`);
    } catch (error) {
      if (error instanceof HttpError && error.status === 404 && index < urls.length - 1) {
        continue;
      }
      lastError = error;
      break;
    }
  }

  throw lastError instanceof Error ? lastError : new Error(`${label} failed`);
}

async function fetchAnyWithFallback(urls: string[], init: RequestInit, label: string): Promise<Response> {
  let lastError: unknown;

  for (let index = 0; index < urls.length; index += 1) {
    const url = urls[index];
    try {
      return await fetchAny(url, init, `${label}#${index + 1}`);
    } catch (error) {
      if (error instanceof HttpError && error.status === 404 && index < urls.length - 1) {
        continue;
      }
      lastError = error;
      break;
    }
  }

  throw lastError instanceof Error ? lastError : new Error(`${label} failed`);
}

async function fetchAny(url: string, init: RequestInit, label: string): Promise<Response> {
  console.log(`[dc-api-test] ${label} request`, {
    url,
    method: init.method || 'GET',
    headers: init.headers,
    body: init.body
  });

  const response = await fetch(url, init);

  console.log(`[dc-api-test] ${label} response`, {
    url,
    status: response.status,
    ok: response.ok,
    headers: Object.fromEntries(response.headers.entries())
  });

  if (!response.ok) {
    const errorText = await response.text().catch(() => '');
    console.error(`[dc-api-test] ${label} error body`, errorText);
    throw new HttpError(label, response.status, errorText);
  }

  return response;
}

class HttpError extends Error {
  status: number;
  body: string;

  constructor(label: string, status: number, body: string) {
    const suffix = body ? ': see result log for payload' : '';
    super(`${label} failed (${status})${suffix}`);
    this.name = 'HttpError';
    this.status = status;
    this.body = body;
  }
}

function parseJson(input: string, source: string): unknown {
  try {
    return JSON.parse(input);
  } catch (error) {
    throw new Error(`Invalid JSON in ${source}: ${toErrorMessage(error)}`);
  }
}

function getSessionId(payload: CreateResponse): string | null {
  if (typeof payload?.sessionId === 'string' && payload.sessionId.trim()) {
    return payload.sessionId;
  }
  return null;
}

function setStatus(el: HTMLElement, text: string): void {
  el.textContent = text;
}

function stringifyJson(value: unknown): string {
  return JSON.stringify(value, null, 2);
}

function clearLog(logEl: HTMLPreElement): void {
  logEl.textContent = '';
}

function appendLog(logEl: HTMLPreElement, message: string): void {
  const next = logEl.textContent ? `${logEl.textContent}\n\n${message}` : message;
  logEl.textContent = next;
}

function appendPayloadLog(logEl: HTMLPreElement, label: string, payload: unknown): void {
  appendLog(logEl, `${label}:\n${formatPayloadForLog(payload)}`);
}

function formatPayloadForLog(payload: unknown): string {
  if (typeof payload === 'string') {
    const parsed = tryParseJson(payload);
    if (parsed !== null) return stringifyJson(parsed);
    return payload;
  }

  if (payload && typeof payload === 'object') {
    const raw = (payload as { raw?: unknown }).raw;
    if (typeof raw === 'string') {
      const parsed = tryParseJson(raw);
      if (parsed !== null) return stringifyJson(parsed);
      return raw;
    }
  }

  return stringifyJson(payload);
}

function tryParseJson(input: string): unknown | null {
  try {
    return JSON.parse(input);
  } catch {
    return null;
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function toErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function getRuntimeConfig(
  verifierPresetSelect: HTMLSelectElement,
  bearerTokenInput: HTMLInputElement
): RuntimeConfig {
  const presetKey = getVerifierPresetKey(verifierPresetSelect.value);
  const verifierBase = VERIFIER_PRESETS[presetKey].verifierBase;
  const bearerToken = bearerTokenInput.value.trim().replace(/^Bearer\s+/i, '');

  return {
    verifierBase,
    bearerToken
  };
}

function getVerifierPresetKey(value: string): VerifierPresetKey {
  if (value in VERIFIER_PRESETS) {
    return value as VerifierPresetKey;
  }
  return DEFAULT_VERIFIER_PRESET;
}

function withAuthorization(
  headers: Record<string, string>,
  bearerToken: string
): Record<string, string> {
  if (!bearerToken) {
    return headers;
  }

  return {
    ...headers,
    Authorization: `Bearer ${bearerToken}`
  };
}

function buildUrl(baseUrl: string, path: string): string {
  const normalizedBase = baseUrl.replace(/\/+$/, '');
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${normalizedBase}${normalizedPath}`;
}

function getOpenApiCandidateUrls(verifierBase: string): string[] {
  const urls = [buildUrl(verifierBase, '/api.json')];
  const normalizedBase = verifierBase.replace(/\/+$/, '');

  if (normalizedBase.startsWith('/')) {
    const [firstSegment] = normalizedBase.split('/').filter(Boolean);
    if (firstSegment) {
      const proxyRootUrl = `/${firstSegment}/api.json`;
      if (!urls.includes(proxyRootUrl)) {
        urls.push(proxyRootUrl);
      }
    }
    return urls;
  }

  try {
    const parsedBase = new URL(normalizedBase);
    const originApiUrl = `${parsedBase.origin}/api.json`;
    if (!urls.includes(originApiUrl)) {
      urls.push(originApiUrl);
    }
  } catch {
    // Ignore malformed URLs and use only default candidate.
  }

  return urls;
}

function handleInitError(error: unknown): void {
  console.error('[dc-api-test] init failed', error);
  const statusEl = document.getElementById('status');
  if (statusEl) {
    statusEl.textContent = `Initialization failed: ${toErrorMessage(error)}`;
  }
}
