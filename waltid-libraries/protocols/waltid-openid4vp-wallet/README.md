# waltid-openid4vp-wallet

This module provides a implementation of the **Holder/Wallet** functionality as defined in the
**OpenID for Verifiable Presentations (OpenID4VP) 1.0 specification**. It is designed to be
integrated into application that needs to act as a digital
wallet, allowing users to present their digital credentials to verifiers.

The core logic is built around handling `Authorization Requests` from verifiers, selecting
appropriate credentials based on a query, and constructing a valid `VP-Token` response according to
the specified credential format and response mode.

## Core Concepts

The OpenID4VP protocol defines an interaction flow between a **Verifier** (which requests proof) and
a **Wallet** (which holds credentials).

1. **Authorization Request**: The Verifier initiates the process by sending an
   `AuthorizationRequest` to the Wallet. This request specifies what credentials are needed
   using **Digital Credentials Query Language (DCQL)**.
2. **Credential Selection**: The Wallet parses the DCQL query and searches its storage for
   credentials that match the Verifier's requirements.
3. **User Consent**: The Wallet prompts the user for consent to share the selected credentials.
4. **Presentation Generation**: Upon consent, the Wallet generates a cryptographic presentation of
   the selected credentials. This process is highly dependent on the credential format (e.g.,
   `sd-jwt`, `mso_mdoc`).
5. **Authorization Response**: The Wallet packages the presentation(s) into a `vp_token` and sends
   it back to the Verifier using a pre-negotiated `response_mode` (e.g., a browser redirect or a
   direct server-to-server post).

This module handles steps 1, 4, and 5.

Step 2 (credential selection and storage) and step 3 (user
consent UI) are specific to the wallet and have to be provided by such.

-----

## Features

* **Specification**: Implements the core flows of the OpenID4VP 1.0 specification.
* **Credential Format Support**: Handles presentation logic for multiple credential
  formats.
    * **`dc+sd-jwt`**: IETF SD-JWT Verifiable Credentials, including selective disclosure and
      key-binding JWTs.
    * **`jwt_vc_json`**: Standard W3C Verifiable Credentials encoded as JWTs.
    * **`mso_mdoc`**: ISO/IEC 18013-5 Mobile Documents (mdocs), such as mobile Driver's Licenses (
      mDL).
* **Flexible Response Modes**: Supports multiple ways to return the presentation to the verifier:
    * `fragment`: Returns the `vp_token` in the URL fragment via browser redirect.
    * `query`: Returns the `vp_token` in the URL query string via browser redirect.
    * `form_post`: Returns a self-submitting HTML form to `POST` the `vp_token`.
    * `direct_post`: Sends the `vp_token` directly to the Verifier's `response_uri` via an HTTP POST
      request.
* **Unopinionated Credential Storage**: The module does not impose any specific database or storage
  solution. You provide a simple function to query your own credential store.
* **Handles Complex Requests**: Parses both URL-encoded authorization requests and advanced
  JWT-Secured Authorization Requests (JAR) passed by `request_uri`.

-----

## Architecture

The module is designed with the following separation of concerns:

### Main Entrypoint: `WalletPresentFunctionality2`

The primary interaction point is the `walletPresentHandling` function. It acts as an orchestrator
that:

1. **Resolves the Authorization Request**: Fetches and parses the request details from the
   `presentationRequestUrl`.
2. **Delegates Credential Selection**: Calls the `selectCredentialsForQuery` lambda function that
   you provide. This is where you hook in your wallet's storage logic.
3. **Generates the `vp_token`**: Iterates through the matched credentials and, based on their
   format, delegates the presentation creation to a specialized **Presenter**.
4. **Constructs the Final Response**: Packages the `vp_token` according to the requested
   `response_mode` and returns a result object (e.g., a redirect URL or a JSON success message).

### Presenters

For each supported credential format, there is a dedicated "Presenter" object responsible for
handling the specific cryptographic operations required to create a valid presentation.

* `SdJwtVcPresenter`: Handles `dc+sd-jwt`. It constructs the final presentation by combining the
  original SD-JWT, the selected disclosures, and a newly created Key Binding JWT.
* `W3CPresenter`: Handles `jwt_vc_json`. It wraps the selected W3C Verifiable Credential JWT
  inside a Verifiable Presentation JWT, signed by the holder's key.
* `MdocPresenter`: Handles `mso_mdoc`. It builds the required `SessionTranscript` and
  `DeviceAuth` structures and assembles the final `DeviceResponse` CBOR object.
* `LDPPresenter`: A placeholder for `ldp_vc` (W3C Credentials with Data Integrity Proofs), which
  is **not yet supported**.

-----

## Usage Example

Integrating the module into a wallet application: The main task is to provide the
logic for credential selection.

```kotlin
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.models.DcqlQuery
import id.walt.crypto.keys.Key
import io.ktor.http.*
import kotlinx.serialization.json.JsonElement

// 1. Prepare holder's key and DID
val holderKey: Key = // ... load holder's private key
val holderDid: String = "did:key:z6M..." // ... holder's DID

// 2. receive a presentation request URL (e.g., from a QR code)
val presentationRequestUrl = "openid4vp://?request_uri=https://verifier.com/request/123".toUrl()

// 3. Implement the credential selection logic
// This lambda connects the module to WALLET-SPECIFIC credential storage.
val selectCredentialsForQuery: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>> =
    { query ->
        // Implement querying wallet database/storage for credentials that match the DCQL query.
        // The walt.id DCQL engine (waltid-dcql) helps with this matching.
        println("Wallet is selecting credentials for query: $query")

        // For this example, we return a hardcoded match.
        // In a real app, you would dynamically find matching credentials, e.g. using waltid-dcql.
        val myStoredCredentials = /* ... fetch credentials from your store ... */
        val matches = DcqlMatcher.match(query, myStoredCredentials)

        matches
    }

// 4. Call the wallet present function to do a presentation as wallet
val presentationResult: Result<JsonElement> = WalletPresentFunctionality2.walletPresentHandling(
    holderKey = holderKey,
    holderDid = holderDid,
    presentationRequestUrl = presentationRequestUrl,
    selectCredentialsForQuery = selectCredentialsForQuery,
    holderPoliciesToRun = null, // Optional: for advanced policy enforcement
    runPolicies = null
)

// 5. Process the result
if (presentationResult.isSuccess) {
    val responseJson = presentationResult.getOrThrow()
    println("Presentation successful! Response: $responseJson")

    // The response will tell you what to do next, e.g., redirect the user.
    // Example for response_mode=fragment or response_mode=query:
    // { "get_url": "https://verifier.com/callback#vp_token=..." }

    // Example for response_mode=direct_post:
    // { "transmission_success": true, "verifier_response": {...} }

} else {
    println("Presentation failed: ${presentationResult.exceptionOrNull()?.message}")
}
```

-----

## Supported Credential Formats

The module provides full presentation support for the following credential formats:

| Format Identifier | Description                                                          | Status              |
|:------------------|:---------------------------------------------------------------------|:--------------------|
| **`dc+sd-jwt`**   | IETF SD-JWT Verifiable Credential. Supports selective disclosure.    | ✅ **Supported**     |
| **`jwt_vc_json`** | W3C Verifiable Credential signed as a JWT.                           | ✅ **Supported**     |
| **`mso_mdoc`**    | Mobile Document (e.g., mDL) compliant with ISO/IEC 18013-5.          | ✅ **Supported**     |
| **`ldp_vc`**      | W3C Verifiable Credential with a Linked Data Proof (Data Integrity). | ❌ **Not Supported** |
