# OpenID4VP request flow, with DCQL matching

(As per OpenID4VP draft 28)

Entities in walt.id terms:

- Verifier: Relying Party
- Wallet: Holder
- User: End-user

## Cross-device flow

For cross-device flow: `request_uri` (to keep QR codes small) + `direct_post` (Wallet sends presentation directly to Verifier)

### Phase 1 (Verifier initiates request & Wallet fetches details)

#### 1. User action on Verifier

The User interacts with the Verifier's application (e.g. website or mobile app), and an action requires credential presentation.

To do this, the Verifier prepares an **Authorization Request**.
To keep the QR code small, it typically hosts the full request object (as an web resource), and generates a `request_uri` pointing to it.

#### 2. Wallet scans QR code & fetches Authorization Request

```http request
GET https://verifier.example.org/request_objects/{unique_request_id}`
```

Verifier responds with the Authorization Request Object, either as signed response (`application/oauth-authz-req+jwt`) or unsigned (plain
JSON).

**→ DCQL is embedded here ←**

*Example: JWT Decoded Payload*

```json
{
  "client_id": "verifier_client_id_or_uri_prefix",
  "response_type": "vp_token",
  "response_mode": "direct_post",
  // Wallet will POST response directly
  "response_uri": "https://verifier.example.org/presentation_response",
  // Verifier's endpoint for the response
  "nonce": "abc123",
  "state": "xyz987",
  // Optional, for Verifier to maintain session
  "scope": "openid",
  // Optional, if also doing SIOPv2
  "dcql_query": {
    // <<<--- DCQL
    "credentials": [
      {
        "id": "identity_credential_query",
        "format": "jwt_vc_json",
        // or "mso_mdoc", "dc+sd-jwt" etc.
        "claims": [
          {
            "path": [
              "credentialSubject",
              "given_name"
            ]
          },
          {
            "path": [
              "credentialSubject",
              "family_name"
            ]
          }
        ]
      }
    ]
  }
  // ... other parameters like client_metadata, request_uri_method (if Wallet POSTs to fetch request)
}
```

##### -> DCQL <-

DCQL is constructed (or a constructed version is returned) in this step.
This has to define:

- which types/formats of credentials the Verifier is looking for (e.g. `jwt_vc_json`, `mso_mdoc`)
- specific claims within those credentials it needs (e.g. `given_name`, `birth_date`)
- Constraints on those claims (e.g. specific values for a claim).
- Constraints on issuers (`trusted_authorities`).
- Combinations of credentials (`credential_sets`).

This object is returned within `dcql_query` within the Authorization Request (or to be precise, in the resolved reference of `request_uri`
in most cases).

### Phase 2 (Wallet processes & User consents)

#### 3. Wallet processes request & User interaction

1. The Wallet parses the Authorization Request
2. **DCQL** The Wallet uses its internal DCQL matcher to evaluate the `dcql_query` against the User's available credentials (waltid-dcql
   with waltid-digital-credentials).
3. Wallet identifies matching credentials
4. Wallet prompts the User for authentication/consent/etc (if needed)

The Wallet receives the Authorization Request and parses the `dcql_query`. It then uses its internal DCQL matching logic:

- To find credentials in the User's possession that satisfy the query.
- To determine which specific claims to selectively disclose if the credential format supports it.

Based on the DCQL match and User consent, the Wallet constructs the Verifiable Presentation(s) and includes them in the `vp_token` parameter
of the Authorization Response sent back to the Verifier.

### Phase 3 (Wallet sends presentation)

#### 4. Wallet sends Authorization Response (Presentation)

Wallet sends POST request to the requested `response_uri`:

```http request
POST https://verifier.example.org/presentation_response
Content-Type: application/x-www-form-urlencoded

vp_token = { ... "my_credential_query_id": ["<presentation_object_1>"] ... } &
state = xyz987
```

- `state` is echoed back from the request
- `vp_token` is a JSON string (see Section 8.1 of OpenID4VP spec)
    - keys are the `id`s from the `CredentialQuery` objects in the DCQL
    - values are arrays of presentation strings/objects
    - Example `vp_token` (decoded JSON string):
      ```json
      {
        "identity_credential_query": [
          "eyJh..."
        ]
      }
      ```

### Phase 4: Verifier acknowledges (Optional redirect)

#### Verifier Responds to Wallet's POST:

HTTP 200 OK

```json
{
  "redirect_uri": "https://verifier.example.org/presentation_complete?session_id=xyz"
}
```

The `redirect_uri` is optional, it could also be an empty 200 OK if no further User interaction is needed further.

## Same-device flow

### Phase 1: Verifier initiates request

#### 1. User Action on Verifier & Verifier Constructs Authorization Request

The User interacts with the Verifier's application (e.g., clicks "Login with Credentials" on a website).

The Verifier constructs the Authorization Request parameters.

Instead of generating a `request_uri`, the Verifier directly redirects the User's browser to the Wallet's Authorization Endpoint.

##### DCQL
Same as cross-device, the Verifier constructs the `dcql_query` object defining its credential requirements.

#### 2. Browser Redirects to Wallet's Authorization Endpoint

Redirect to the Wallet's registered/known authorization endpoint.

Users' browser issues a HTTP GET (from the redirect):

- `GET https://wallet.example.org/xyz-authorize?...` - if the Wallet is a web application
- `GET customwallet://authorize?...` - if the Wallet is a native app and the browser/OS can handle it
- Universal Link / App Link

```http request
GET https://wallet.example.com/xyz-authorize
    ?response_type=vp_token
    &client_id=verifier_client_id_on_same_device
    &redirect_uri=https%3A%2F%2Fverifier.example.org%2Fcallback // Verifier's page to receive the response&nonce = n-0S6_WzA2Mj_same_device
    &state=another_state_value
    &dcql_query=%7B%22credentials%22%3A%5B%7B%22id%22%3A%22did_auth_query%22%2C%22format%22%3A%22ldp_vc%22%2C%22claims%22%3A%5B%7B%22path%22%3A%5B%22type%22%5D%2C%22values%22%3A%5B%22VerifiableCredential%22%2C%22DIDAuthenticationCredential%22%5D%7D%5D%7D%5D%7D
```

The `dcql_query` is a URL-encoded JSON string. Decoded, it would look like:

```json
{
  "credentials": [
    {
      "id": "did_auth_query",
      "format": "ldp_vc",
      "claims": [
        {
          "path": [
            "type"
          ],
          "values": [
            "VerifiableCredential",
            "DIDAuthenticationCredential"
          ]
        }
      ]
    }
  ]
}
```

### Phase 2: Wallet processes & User consents

#### 3. Wallet processes request & User interaction:

- The Wallet application (now active on the user's device) parses the Authorization Request parameters from the incoming URL.
- DCQL Processing: The Wallet uses its internal DCQL matcher to evaluate the `dcql_query` against the User's available credentials.
- The Wallet identifies matching credentials.
- The Wallet prompts the User for authentication (if needed) and consent to share the selected credentials/claims with the Verifier.

### Phase 3: Wallet sends presentation

#### Wallet redirects back to Verifier with Authorization Response

Users browser does HTTP GET to `redirect_uri`

For response_type=vp_token, the response parameters are typically returned in the URL fragment (#).

```
https://verifier.example.org/callback#
vp_token=%7B%22did_auth_query%22%3A%5B%22%7B%5C%22%40context%5C%22%3A%5B%5C%22https%3A%2F%2Fwww.w3.org%2F2018%2Fcredentials%2Fv1%5C%22%5D%2C...%7D%22%5D%7D
&state=another_state_value
```

The `vp_token` is a URL-encoded JSON string. Decoded, it would look like:

```json
{
  "did_auth_query": [
    {
      "@context": [
        "https://www.w3.org/2018/credentials/v1"
      ],
      "type": [
        "VerifiablePresentation",
        "DIDAuthenticationPresentation"
      ],
      "verifiableCredential": [
        /* ... credential data ... */
      ],
      "proof": {
        /* ... presentation proof ... */
      }
    }
  ]
}
```

### Phase 4: Verifier receives and processes presentation

#### Verifier Processes Authorization Response

- The Verifier's page at the `redirect_uri` (e.g., `https://verifier.example.org/callback`) uses client-side JavaScript to parse the
  parameters from the URL fragment (`window.location.hash`).
- It extracts the `vp_token` and the `state`
- The Verifier validates the `state` against the one it sent initially.
- The Verifier validates the `vp_token` (including the Verifiable Presentation(s) within it). This might involve sending it to its backend
  for cryptographic verification.
- If successful, the Verifier grants the user access or completes the transaction.
