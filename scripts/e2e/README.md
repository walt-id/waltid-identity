# E2E Testing: Mobile Wallet Demos

End-to-end tests for the Android and iOS wallet demo apps against the local enterprise stack exposed via ngrok.

## Prerequisites

- Enterprise stack running — see [waltid-enterprise-quickstart](https://github.com/walt-id/waltid-enterprise-quickstart)
  ```bash
  cd /path/to/waltid-enterprise-quickstart
  docker compose up -d
  cd cli && npm install && npx tsx walt.ts --recreate
  ```
- ngrok tunnel active, pointing to port 7500:
  ```bash
  ngrok http 7500 --domain=<your-ngrok-domain>
  ```
- Host alias created for the organization — done automatically by `--recreate` if `HOST_ALIAS_DOMAIN` is set in the quickstart's `cli/walt.env`

### Setup

```bash
cd scripts/e2e
cp e2e.env.example e2e.env
# Edit e2e.env: set HOST_ALIAS_DOMAIN to your ngrok domain
```

### Platform-specific

**Android:**
- Emulator running (or physical device connected via USB with ADB)
- App installed (or use `--build` flag)
- Ngrok workarounds patch applied (see below)

**iOS:**
- Simulator booted
- App installed (or use `--build` flag)
- macOS with Xcode command-line tools

## Running

```bash
cd scripts/e2e

# Android
./e2e-android.sh              # receive + present
./e2e-android.sh --build      # rebuild APK first
./e2e-android.sh --receive    # only receive
./e2e-android.sh --present    # only present (credential must be in memory)

# iOS
./e2e-ios.sh                  # receive + present
./e2e-ios.sh --build          # rebuild framework + app first
./e2e-ios.sh --receive        # only receive
./e2e-ios.sh --present        # only present (credential must be in memory)
```

## Configuration

All configuration is sourced from `e2e.env` and can be overridden via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `HOST_ALIAS_DOMAIN` | *(required)* | Your ngrok domain |
| `PORT` | `7500` | Enterprise API port |
| `ADMIN_EMAIL` | `admin@walt.id` | Admin credentials |
| `ADMIN_PASSWORD` | `admin123456` | Admin credentials |
| `ORGANIZATION` | `waltid` | Organization slug |
| `TENANT` | `waltid-tenant01` | Tenant slug |
| `IDENTITY_DIR` | *(auto: repo root)* | Path to `waltid-identity` repo root |
| `IOS_SIMULATOR_ID` | *(auto: first booted sim)* | Booted simulator UUID |
| `IOS_BUNDLE_ID` | `waltid.iosApp` | iOS app bundle ID |
| `ANDROID_PACKAGE` | `id.walt.walletdemo` | Android app package |
| `UI_POLL_TIMEOUT` | `45` | Seconds to wait for UI state changes (Android) |

## Ngrok Workarounds Patch

The enterprise API currently returns `http://` URLs in its OpenID metadata because it doesn't honor `X-Forwarded-Proto` from the ngrok proxy. This causes two issues on Android:

1. **Cleartext HTTP blocked** — Android 9+ blocks non-HTTPS traffic by default
2. **307 redirects on POST** — ngrok redirects HTTP→HTTPS, but Ktor doesn't always follow 307 for POST

A patch is included at `patches/ngrok-workarounds.patch`:

```bash
# Apply (from the waltid-identity repo root)
git apply scripts/e2e/patches/ngrok-workarounds.patch

# Revert
git apply -R scripts/e2e/patches/ngrok-workarounds.patch
```

**These workarounds become unnecessary** once the enterprise API installs Ktor's `ForwardedHeaders` plugin.

### What the patch changes

| File | Change |
|------|--------|
| `AndroidManifest.xml` | `android:usesCleartextTraffic="true"` |
| `WalletIssuanceHandler.kt` | Follow 307 redirects for credential/nonce POST requests |
| `TokenRequestBuilder.kt` | Follow 307 redirects for token POST requests |

## How It Works

### Android
1. Creates a credential offer via the ngrok-exposed API
2. Delivers the `openid-credential-offer://` URL as a deep link via `adb`
3. Finds and taps the Receive button using UI Automator
4. Polls UI state until receive completes
5. Creates a verification session
6. Delivers the `openid4vp://` deep link (without restarting — credentials are in-memory)
7. Taps Present button
8. Confirms verifier session status is `SUCCESSFUL` via API

### iOS
1. Pre-approves URL schemes in the simulator's plist (no confirmation dialog)
2. Launches app and waits for bootstrap
3. Creates a credential offer and delivers it as a deep link — app auto-triggers receive
4. Creates a verification session and delivers the deep link — app auto-triggers present
5. Confirms verifier session status is `SUCCESSFUL` via API

## Important Notes

- **In-memory credentials**: Both apps use in-memory storage. Don't restart the app between receive and present.
- **Offers expire** in ~5 minutes. The scripts create fresh ones each run.
- **iOS has no UI automator**: The iOS app auto-triggers receive/present on deep link arrival.
- **Android needs the ngrok patch**: Without it, cleartext blocks and redirect issues prevent the flow from completing.
- **iOS doesn't need the patch**: iOS follows redirects natively.
- **waltid-identity branch**: The iOS app needs fixes from `feature/wal-1033-initial-wallet-sdk-setup-clean` (signJws, importPEM, catch Throwable, deep link auto-trigger).

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Enterprise API not reachable` | Stack not running | Start via quickstart repo |
| `ngrok tunnel not reachable` | Tunnel not active | `ngrok http 7500 --domain=...` |
| `Failed to get credential offer` | Issuer not configured | `npx tsx walt.ts --recreate` in quickstart CLI |
| Android receive hangs/fails | Ngrok patch not applied | `git apply scripts/e2e/patches/ngrok-workarounds.patch` |
| iOS app crashes on deep link | Wrong waltid-identity branch | Check out `feature/wal-1033-initial-wallet-sdk-setup-clean` |
| `No booted iOS simulator` | Simulator not started | `xcrun simctl boot <device-id>` |
| Verifier status PENDING | Presentation still in flight | Increase sleep or check logs |
