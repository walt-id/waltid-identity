# WAL-1033 Demo Recording Runbook

## One-Time Setup (before first recording)

```bash
cd /Users/szipe/dev/walt-id/waltid-unified-build/waltid-identity/scripts/demo
./setup.sh
```

Start ngrok in a separate terminal (keep running):

```bash
ngrok http 7500 --domain=<your-ngrok-domain>
```

## Pre-Recording Checklist

```bash
./check.sh
```

Ensure:
- Android emulator running with demo app installed
- iOS simulator running with demo app installed
- Enterprise UI open at http://waltid.enterprise.localhost (port 80, via Caddy)
- Logged in as `admin@walt.id` / `admin123456`

## Why ./offer.sh and ./verify.sh instead of the Enterprise UI buttons?

The Enterprise API embeds whatever hostname it receives into credential offer and verification URLs.
The UI calls the API via `waltid.enterprise.localhost` (through Caddy), so generated URLs contain that host.
Android emulators and iOS simulators can't resolve `waltid.enterprise.localhost` — it's a local-only domain.

The helper scripts call the API via the ngrok tunnel (`<your-ngrok-domain>`), so the generated URLs
point to ngrok, which is publicly resolvable and routes back to the local API. Mobile devices can reach it.

The UI can't be reconfigured to call via ngrok because it hardcodes `{orgName}.{baseDomain}` for API calls
(i.e., it would try `waltid.<your-ngrok-domain>` which doesn't exist on ngrok's free tier).

**Use the UI for:** browsing services, viewing issuer/verifier config, checking verification results.
**Use the scripts for:** generating offer/verify URLs that mobile devices can actually reach.

## Recording Flow: Credential Issuance

1. Run: `./offer.sh` (generates offer via ngrok, delivers to both Android + iOS)
2. Both apps show the received mDL credential (family_name: Doe, given_name: John, etc.)
3. **Enterprise UI:** Optionally show the issuer2-noattest service config for context

Note: The Enterprise UI "Create Offer" button generates URLs with `waltid.enterprise.localhost` which
emulators/simulators can't reach. Always use `./offer.sh` for the actual credential delivery.

## Recording Flow: Credential Presentation

1. Run: `./verify.sh` (creates session via ngrok, delivers openid4vp:// to both Android + iOS)
2. Tap **Present** on both wallet apps
3. **Enterprise UI:** Navigate to Verifiers → `verifier2` → click the session → shows **SUCCESSFUL** + presented claims
4. (Optional) Run `./status.sh` in terminal for a technical verification summary

## Recording Flow: Combined (recommended order)

1. Show Enterprise UI dashboard briefly (services, issuer config)
2. `./offer.sh` → both apps receive credential
3. `./verify.sh` → both apps present credential
4. Enterprise UI → verifier session detail → SUCCESSFUL
5. `./status.sh` → terminal shows full policy results

## Tips for Clean Recording

- **Window layout:** Enterprise UI on one half, emulator + simulator side-by-side on the other half
- **Pre-open tabs:** Have the Issuer "Create Offer" and Verifier "Create Session" pages ready in separate tabs
- **Offer TTL:** Offers expire in ~5 minutes. If one expires, just create a new one in the UI
- **In-memory credentials:** Don't restart the wallet apps between issuance and presentation — credentials live in memory only
- **If app needs restart:** Re-do issuance first (get a fresh credential), then present
- **Clipboard tip:** Copy URL once, paste into Android first (Cmd+V in emulator), then iOS (Cmd+V in simulator window)

## After Recording

```bash
./teardown.sh
```

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| "Connection refused" in wallet app | Android: `adb reverse tcp:7500 tcp:7500`. iOS: ensure ngrok is running |
| 404 from enterprise API | Host alias not set for ngrok domain. Re-run `./setup.sh` |
| Offer URL doesn't work / expired | Create a new offer in the UI (offers live ~5 min) |
| App crash on receive | Likely a crypto `TODO` hit. Check `adb logcat` (Android) or Xcode console (iOS) |
| Verifier session stays "IN_PROGRESS" | Wallet may have failed silently. Check app logs, try again |
| Enterprise UI won't load | Access via `http://waltid.enterprise.localhost` (port 80, Caddy). macOS resolves `*.localhost` natively. Don't use `localhost:7501` directly — the UI needs org-prefixed domain routing. |
| Can't login to Enterprise UI | Stack needs `--recreate`. Run `./setup.sh` again |
| Enterprise UI shows CORS/network errors | Caddy can't reach UI container. Run: `docker rm -f waltid-enterprise-ui && docker run -d --name waltid-enterprise-ui --network mongo-network --network-alias waltid-enterprise-ui -p 7501:3000 waltid/waltid-enterprise-ui:latest` |
