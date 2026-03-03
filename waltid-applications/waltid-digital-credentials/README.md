# dc-api-test

Standalone web test app for validating the DC API flow directly against verifier endpoints.

## What it does
- Loads OpenAPI docs from the selected verifier (`/api.json`)
- Lists `POST /verification-session/create` examples containing `dc_api`
- Lets you edit the selected JSON payload
- Executes flow:
  1. `POST /verification-session/create`
  2. `GET /verification-session/{sessionId}/request`
  3. `navigator.credentials.get(...)`
  4. `POST /verification-session/{sessionId}/response`
  5. Polls `GET /verification-session/{sessionId}/info` every 10 seconds
- Polling rule:
  - continue while `status === "IN_USE"`
  - success when `status === "SUCCESSFUL"`
  - any other status is treated as failure
- Logs all request/response objects to browser console
- Shows process and final payloads in the bottom `Result Log` field

## UI controls
- Verifier preset:
  - Open Source (`https://verifier2.portal.test.waltid.cloud`)
  - Enterprise (proxied path)
- Optional bearer token for authenticated verifier deployments
- Reload Swagger examples button

## Startup
From repository root:

```bash
npm run dev --workspace apps/dc-api-test
```

Shortcut:

```bash
npm run dev:dc-api-test
```

## Docker
Build from repository root (same image tag used by web-demo flow):

```bash
docker build --no-cache -f apps/dc-api-test/Dockerfile -t waltid/digital-credentials .
```
