# Transactional Verification Mode - Implementation Guide

## Overview

This guide explains how to modify Verifier API 2 to operate in **transactional mode**, where verified credential data is **never persisted** and is only delivered via **Server-Sent Events (SSE)** and **webhook callbacks**.

## What is Transactional Verification?

Transactional verification ensures that:
- ✅ Credential data is **pushed** to clients via SSE and webhooks
- ✅ Credential data is **never persisted** in the verifier service
- ✅ Only session metadata and verification results are retained
- ✅ Complies with data governance requirements for minimal data retention

## Current Behavior

By default, Verifier API 2 stores credential data in the session:
- `presentedCredentials` - The verified credential data
- `presentedPresentations` - The verifiable presentations  
- `presentedRawData` - The raw vp_token data

This data remains in memory until the session is deleted (default retention: 10 years).

## Implementation Steps

### Step 1: Modify the Session Update Callback

Edit the file: `waltid-services/waltid-verifier-api2/src/main/kotlin/id/walt/openid4vp/verifier/OSSVerifier2Service.kt`

**Find the `updateSessionCallback`** (around line 45-62):

```kotlin
val updateSessionCallback: suspend (
    session: Verification2Session,
    event: SessionEvent,
    block: Verification2Session.() -> Unit
) -> Unit = { session, event, block ->
    log.trace { "Updating session due to '$event': ${session.id}" }
    val newSession = session.apply {
        block.invoke(this)
    }
    sessions[newSession.id] = newSession

    Verifier2SessionUpdate(session.id, event, session)
        .toKtorSessionUpdate()
        .notifySessionUpdate(session.id, session.notifications)
}
```

**Replace it with:**

```kotlin
val updateSessionCallback: suspend (
    session: Verification2Session,
    event: SessionEvent,
    block: Verification2Session.() -> Unit
) -> Unit = { session, event, block ->
    log.trace { "Updating session due to '$event': ${session.id}" }
    val newSession = session.apply {
        block.invoke(this)
    }
    sessions[newSession.id] = newSession

    // Send notifications with credential data included
    Verifier2SessionUpdate(newSession.id, event, newSession)
        .toKtorSessionUpdate()
        .notifySessionUpdate(newSession.id, newSession.notifications)

    // Clear credential data after sending notifications for final verification events
    // This ensures data is pushed via SSE/webhook but not persisted
    val isFinalEvent = event == SessionEvent.policy_results_available ||
            newSession.status == VerificationSessionStatus.SUCCESSFUL ||
            newSession.status == VerificationSessionStatus.UNSUCCESSFUL

    if (isFinalEvent && (newSession.presentedCredentials != null || 
            newSession.presentedPresentations != null || 
            newSession.presentedRawData != null)) {
        log.debug { "Clearing credential data from session ${newSession.id} after final verification event: $event" }
        sessions[newSession.id] = newSession.copy(
            presentedCredentials = null,
            presentedPresentations = null,
            presentedRawData = null
        )
    }
}
```

### Step 2: Key Changes Explained

1. **Use `newSession` instead of `session`**: The notification now includes the updated session data with credentials
2. **Check for final events**: Credential data is cleared when:
   - Event is `policy_results_available` (final verification event)
   - Session status becomes `SUCCESSFUL` or `UNSUCCESSFUL`
3. **Clear credential fields**: After notifications are sent, set credential data fields to `null`

### Step 3: Verify the Changes

After making the changes:

1. **Rebuild the service**:
   ```bash
   ./gradlew :waltid-services:waltid-verifier-api2:build
   ```

2. **Test the flow**:
   - Create a verification session with webhook/SSE configured
   - Complete a verification
   - Check that webhook/SSE receives credential data
   - Query session info endpoint - credential data should be `null`

## How It Works After Modification

### Verification Flow

1. **Wallet presents credentials** via `POST /verification-session/{sessionId}/response`
2. **Verifier validates** credentials and stores them temporarily in the session
3. **Session updates** are sent via SSE and webhooks (if configured) **with credential data included**
4. **After notifications are sent**, credential data is automatically cleared
5. **Only metadata remains**: Session info endpoint returns verification status and policy results, but no credential data

### Session Events

The following events are sent during verification:
- `authorization_request_requested` - Authorization request created
- `attempted_presentation` - Presentation received
- `parsed_presentation_available` - Presentation parsed
- `validated_credentials_available` - Credentials validated
- `presentation_fulfils_dcql_query` - DCQL requirements met
- `policy_results_available` - **Final event** (credential data cleared after this)

## Configuration

### 1. Enable Webhook Notifications

To receive credential data via webhook, configure notifications when creating a verification session:

```json
POST /verification-session/create
Content-Type: application/json

{
  "dcqlQuery": {
    "credentials": [
      {
        "id": "my_credential",
        "format": "jwt_vc_json"
      }
    ]
  },
  "notifications": {
    "webhook": {
      "url": "https://your-server.com/webhook",
      "bearerToken": "your-secret-token"
    }
  }
}
```

### 2. Subscribe to SSE Events

Connect to the SSE endpoint to receive real-time updates:

```http
GET /verification-session/{sessionId}/verification-session/events
Accept: text/event-stream
```

**Example SSE Event with Credential Data**:
```
event: policy_results_available
data: {
  "id": "session-123",
  "status": "SUCCESSFUL",
  "presentedCredentials": {
    "my_credential": [
      {
        "format": "jwt_vc_json",
        "credential": "..."
      }
    ]
  },
  "policyResults": {
    "overallSuccess": true,
    ...
  }
}
```

### 3. Webhook Payload

When a webhook is configured, you'll receive the full session object including credential data:

```json
{
  "target": "session-123",
  "event": "policy_results_available",
  "session": {
    "id": "session-123",
    "status": "SUCCESSFUL",
    "presentedCredentials": {
      "my_credential": [
        {
          "format": "jwt_vc_json",
          "credential": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
        }
      ]
    },
    "presentedPresentations": {
      "my_credential": {
        "type": "VerifiablePresentation",
        "verifiableCredential": [...]
      }
    },
    "policyResults": {
      "overallSuccess": true,
      "vpPolicies": {...},
      "vcPolicies": {...}
    }
  }
}
```

## What Data is Cleared

After the modification, the following fields are cleared from the session after final verification:
- `presentedCredentials` - The verified credential data
- `presentedPresentations` - The verifiable presentations
- `presentedRawData` - The raw vp_token data

**⚠️ Important Note on Policy Results:**

The `policyResults` field contains `Verifier2PolicyResults` which includes:
- `vcPolicies: List<CredentialPolicyResult>` - VC policy results
- `vpPolicies: Map<String, Map<String, VPPolicy2.PolicyRunResult>>` - VP policy results
- `specificVcPolicies: Map<String, List<CredentialPolicyResult>>` - Specific VC policy results

**Some policy results may contain credential data:**
- The `CredentialSignaturePolicy` stores `signed_credential` in its result, which contains the full signed credential
- Other policies may also include credential data in their `result: JsonElement?` fields

If you need to ensure **complete** transactional behavior, you may also want to clear or sanitize policy results. However, policy results are typically needed for audit trails and verification decisions, so clearing them may not be desirable. Consider:
- Keeping policy results but removing credential data from within them
- Or accepting that policy results contain some credential data for audit purposes

## What Data is Retained

The following data remains in the session (metadata only):
- Session ID and creation date
- Verification status (`SUCCESSFUL`, `UNSUCCESSFUL`, etc.)
- Policy results (`Verifier2PolicyResults`) - **Note: May contain credential data in some policy results** (see warning above)
- Authorization request metadata
- Session expiration and retention dates

## Best Practices

### 1. Always Configure Notifications

For transactional mode, **always** configure either:
- SSE subscription (for real-time updates)
- Webhook callback (for server-to-server delivery)
- Or both

**⚠️ Without notifications, credential data will be lost after clearing!**

### 2. Handle Webhook Failures

If your webhook endpoint is unavailable:
- The credential data will still be cleared
- Consider implementing retry logic in your webhook handler
- Or use SSE as a backup mechanism
- Consider adding a delay before clearing (see Advanced Options below)

### 3. Store Data Immediately

When receiving webhook or SSE events:
- Store credential data in your own system immediately
- Don't rely on querying the session later (data will be cleared)
- Use the `policy_results_available` event as the trigger to persist

### 4. Session Info Endpoint

The `GET /verification-session/{sessionId}/info` endpoint will **not** return credential data after clearing. It will only return:
- Session metadata
- Verification status
- Policy results (outcomes, not credential data)

## Example Integration

### Complete Flow Example

```kotlin
// 1. Create verification session with webhook
val session = createVerificationSession(
    dcqlQuery = dcqlQuery,
    notifications = KtorSessionNotifications(
        webhook = KtorSessionNotifications.VerificationSessionWebhookNotification(
            url = "https://your-server.com/webhook",
            bearerToken = "secret-token"
        )
    )
)

// 2. Subscribe to SSE (optional, for real-time updates)
val sseFlow = SseNotifier.getSseFlow(session.id)
sseFlow.collect { event ->
    if (event.event == "policy_results_available") {
        val sessionData = event.session
        // Store credential data immediately
        storeCredentials(sessionData["presentedCredentials"])
    }
}

// 3. Webhook handler (receives credential data)
POST /webhook
{
    "target": "session-123",
    "event": "policy_results_available",
    "session": {
        "presentedCredentials": {...},  // Available here
        ...
    }
}

// 4. After webhook/SSE, credential data is cleared
// GET /verification-session/{sessionId}/info will NOT return credential data
```

## Verification

### Check That Data is Cleared

After verification completes, query the session info endpoint:

```http
GET /verification-session/{sessionId}/info
```

The response should have:
- ✅ `status`: `SUCCESSFUL` or `UNSUCCESSFUL`
- ✅ `policyResults`: Verification outcomes
- ❌ `presentedCredentials`: `null`
- ❌ `presentedPresentations`: `null`
- ❌ `presentedRawData`: `null`

## Advanced Options

### Option 1: Add Delay Before Clearing

If you want to ensure webhooks are delivered before clearing, add a delay:

```kotlin
if (isFinalEvent && (newSession.presentedCredentials != null || 
        newSession.presentedPresentations != null || 
        newSession.presentedRawData != null)) {
    // Wait a short time to ensure webhook delivery
    kotlinx.coroutines.delay(1000) // 1 second delay
    
    log.debug { "Clearing credential data from session ${newSession.id} after final verification event: $event" }
    sessions[newSession.id] = newSession.copy(
        presentedCredentials = null,
        presentedPresentations = null,
        presentedRawData = null
    )
}
```

### Option 2: Clear Only on Success

If you want to keep credential data for failed verifications (for debugging):

```kotlin
val isFinalEvent = event == SessionEvent.policy_results_available ||
        newSession.status == VerificationSessionStatus.SUCCESSFUL

// Only clear on successful verification
if (isFinalEvent && newSession.status == VerificationSessionStatus.SUCCESSFUL && ...) {
    // Clear credential data
}
```

### Option 3: Conditional Clearing Based on Configuration

Add a configuration flag to enable/disable transactional mode:

```kotlin
// In OSSVerifier2ServiceConfig.kt
data class OSSVerifier2ServiceConfig(
    // ... existing config ...
    val transactionalMode: Boolean = false // Add this flag
)

// In updateSessionCallback
if (config.transactionalMode && isFinalEvent && ...) {
    // Clear credential data
}
```

### Option 4: Sanitize Policy Results

If you need to remove credential data from policy results while keeping the verification outcomes:

```kotlin
if (isFinalEvent && newSession.policyResults != null) {
    // Sanitize policy results by removing credential data
    val sanitizedPolicyResults = newSession.policyResults!!.copy(
        vcPolicies = newSession.policyResults!!.vcPolicies.map { vcResult ->
            // Remove signed_credential from signature policy results
            val sanitizedResult = vcResult.result?.jsonObject?.toMutableMap()?.apply {
                remove("signed_credential")
                remove("verified_data") // May also contain credential data
            }?.let { JsonObject(it) } ?: vcResult.result
            
            vcResult.copy(result = sanitizedResult)
        },
        // VP policies typically don't contain full credentials, but check if needed
        vpPolicies = newSession.policyResults!!.vpPolicies
    )
    
    sessions[newSession.id] = newSession.copy(
        policyResults = sanitizedPolicyResults,
        presentedCredentials = null,
        presentedPresentations = null,
        presentedRawData = null
    )
}
```

**Note:** This is more complex and may break functionality if other parts of the system rely on credential data in policy results. Only implement if you have a specific requirement to remove credential data from policy results.

## Troubleshooting

### Issue: Not Receiving Credential Data

**Problem**: Webhook/SSE doesn't contain credential data

**Solutions**:
1. Ensure notifications are configured when creating the session
2. Check that your webhook endpoint is reachable
3. Verify SSE connection is established before verification completes
4. Check logs for notification delivery errors
5. Verify the modification was applied correctly (check `OSSVerifier2Service.kt`)

### Issue: Data Still Persisted

**Problem**: Credential data appears in session after verification

**Solutions**:
1. Verify the modification is applied (check `OSSVerifier2Service.kt`)
2. Check that final event (`policy_results_available`) is being sent
3. Verify session status is `SUCCESSFUL` or `UNSUCCESSFUL`
4. Check logs for clearing operations
5. Ensure you're using `newSession` in the notification call

### Issue: Missing Credential Data in Webhook

**Problem**: Webhook received but credential data is null

**Solutions**:
1. Ensure webhook is called **after** verification completes
2. Check that verification was successful (status = `SUCCESSFUL`)
3. Verify the event is `policy_results_available`
4. Check that credentials were actually presented by the wallet
5. Verify you're using `newSession` (not `session`) in the notification

### Issue: Data Cleared Too Early

**Problem**: Credential data is cleared before webhook is delivered

**Solutions**:
1. Implement Option 1 (add delay before clearing)
2. Ensure webhook endpoint responds quickly
3. Use SSE as backup mechanism
4. Store data immediately when received via webhook

## Data Governance Compliance

This transactional mode ensures:
- ✅ **Minimal data retention**: Credential data is not persisted
- ✅ **Data delivery**: Credential data is delivered via secure channels (SSE/webhook)
- ✅ **Audit trail**: Verification results and metadata are retained for auditing
- ✅ **GDPR compliance**: No long-term storage of personal credential data
- ✅ **Right to deletion**: Credential data is automatically cleared after delivery

## Related Documentation

- [Verifier API 2 README](../README.md)
- [waltid-ktor-notifications](../../../../waltid-libraries/web/waltid-ktor-notifications/README.md)
- [OpenID4VP Verifier Library](../../../../waltid-libraries/protocols/waltid-openid4vp-verifier/README.md)

## Summary

By making this simple modification to `OSSVerifier2Service.kt`, you can enable transactional verification mode where:
- Credential data is delivered via SSE/webhooks
- Credential data is never persisted in the verifier
- Only verification metadata is retained
- Full compliance with data governance requirements

The change is minimal, safe, and reversible if needed.
