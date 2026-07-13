# One-Click Manual Testing - User Guide

## Quick Start

When you run a VCI Wallet conformance test, you'll see this in the terminal:

```
═════════════════════════════════════════════════════════════════════════════════
 VCI Wallet Test Plan: SD-JWT VC + DPoP + Authorization Code
═════════════════════════════════════════════════════════════════════════════════
  Format: sd_jwt_vc
  Grant: authorization_code
  Sender: dpop
  Client Auth: private_key_jwt

Test plan created: vci_wallet_sdjwt_dpop
Test modules: 1
   - oid4vci-1_0-wallet-test-credential-issuance

[1/1] Running: oid4vci-1_0-wallet-test-credential-issuance
   Test ID: QRodiX9BOQMsf7w
   View: https://localhost.emobix.co.uk:8443/log-detail.html?log=QRodiX9BOQMsf7w
   Credential Offer Endpoint: http://10.0.0.79:7007/credential-offer
   Note: Open the credential offer URL in your browser to start issuance

   Status: WAITING (visit credential offer URL to continue)
```

## What to Do

### Step 1: Watch the Console

When the conformance suite sends the credential offer, you'll see:

```
[VCI Adapter] Received credential offer
[VCI Adapter] Grants - preAuth: false, authCode: true  
[VCI Adapter] Stored offer with ID: a3f9e2b1
[VCI Adapter] ✨ Open this URL in your browser: http://127.0.0.1:7007/offer/a3f9e2b1
```

### Step 2: Open the URL

**Copy and paste the URL into your browser** (or just click it if your terminal supports it):

```
http://127.0.0.1:7007/offer/a3f9e2b1
```

### Step 3: You'll See the Offer Page

**Example for SD-JWT VC test:**
```
🎫 Credential Offer Received

Issuer: https://localhost.emobix.co.uk:8443/...
Credentials: org.iso.18013.5.1.mDL (format: sd_jwt_vc)
Grant Type: Authorization Code (OAuth)

[🚀 Start Issuance]
```

**Example for ISO mdoc test:**
```
🎫 Credential Offer Received

Issuer: https://localhost.emobix.co.uk:8443/...
Credentials: org.iso.18013.5.1.mDL (format: mso_mdoc)
Grant Type: Authorization Code (OAuth)

[🚀 Start Issuance]
```

**Important:** The credential configuration ID (e.g., `org.iso.18013.5.1.mDL`) is just an identifier - it doesn't tell you the format! The actual format (`sd_jwt_vc` vs `mso_mdoc`) is fetched from the issuer's metadata and shown in parentheses.

```
┌────────────────────────────────────────────────────┐
│  🎫 Credential Offer Received                      │
├────────────────────────────────────────────────────┤
│                                                     │
│  Issuer: https://localhost.emobix.co.uk:8443/...  │
│  Credentials: org.iso.18013.5.1.mDL               │
│  Grant Type: Authorization Code (OAuth)            │
│                                                     │
│  ┌────────────────────────────────────────────┐  │
│  │      🚀 Start Issuance                     │  │
│  └────────────────────────────────────────────┘  │
│                                                     │
└────────────────────────────────────────────────────┘
```

**Click the "Start Issuance" button** → You'll be automatically redirected to the authorization page.

### Step 4: OAuth Login

The conformance suite will show you a login page:

```
┌────────────────────────────────────────────────────┐
│  OAuth Authorization                               │
├────────────────────────────────────────────────────┤
│                                                     │
│  Wallet wants to receive:                          │
│  • org.iso.18013.5.1.mDL                          │
│                                                     │
│  Username: [ user1           ]                     │
│  Password: [ ********         ]                     │
│                                                     │
│  ┌──────────┐  ┌──────────┐                       │
│  │ Authorize │  │ Deny     │                       │
│  └──────────┘  └──────────┘                       │
│                                                     │
└────────────────────────────────────────────────────┘
```

**Enter credentials and click "Authorize".** (The conformance suite provides test credentials)

### Step 5: Success!

After authorization, you'll see:

```
┌────────────────────────────────────────────────────┐
│  ✅ Authorization Complete                         │
├────────────────────────────────────────────────────┤
│                                                     │
│  Credential received                               │
│                                                     │
│  You can close this window.                        │
│                                                     │
└────────────────────────────────────────────────────┘
```

And in the terminal:

```
   Status: FINISHED
   Result: PASSED ✅

═════════════════════════════════════════════════════════════════════════════════
 Results
═════════════════════════════════════════════════════════════════════════════════
  Total: 1
  Passed: 1

  [0] ✓ QRodiX9BOQMsf7w: PASSED
═════════════════════════════════════════════════════════════════════════════════
```

## That's It!

**One URL open + one button click = test complete.** 🎉

No complex automation, no Docker networking issues, just simple browser-based testing.
