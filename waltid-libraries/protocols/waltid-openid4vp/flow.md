# OpenID4VP request flow, with DCQL matching

(As per OpenID4VP draft 28)

Entities in walt.id terms:

- Verifier: Relying Party
- Wallet: Holder
- User: End-user

## Protocol miscellaneous

### Constructing Authorization Request

#### Key considerations:

- **Identifying the Verifier (`client_id` & prefixes - see Section 5.9)**
    - **`client_id` (REQUIRED):** This is how the Wallet identifies the Verifier.
        - **Pre-registered Client:** If the Wallet knows the Verifier beforehand (e.g., through a developer portal), this is a simple
          identifier.
        - **Client Identifier Prefixes:** For dynamic registration or specific trust mechanisms, prefixes are used:
            - `redirect_uri:{your_redirect_uri}`: The Wallet uses the redirect URI as the client identifier. Metadata is passed via
              `client_metadata`. Cannot be used for signed requests.
            - `x509_san_dns:{dns_name}`: Verifier authenticates by signing the request with a key corresponding to an X.509 certificate
              whose SAN DNS matches.
            - `decentralized_identifier:{did_string}`: Verifier authenticates using a DID.
            - `verifier_attestation:{subject_in_attestation}`: Verifier authenticates using a Verifier Attestation JWT.
            - `openid_federation:{entity_id}`: Verifier identity and metadata are resolved via OpenID Federation.
- **Specifying what to present (`dcql_query` - see Section 6)**
    - **`dcql_query` (REQUIRED, unless using `scope` for a pre-defined query):**: This is a JSON object detailing the credential
      requirements.
      This is the core of the presentation request. Replaced the "Presentation Definition".
        - **`credentials` array:**: Lists each type of credential requested.
            - `id`: A Verifier-defined ID for this specific credential query (used to map responses).
            - `format`: The credential format (e.g., `jwt_vc_json`, `ldp_vc`, `mso_mdoc`, `dc+sd-jwt`).
            - `claims`: Array of claims to be selectively disclosed. Each has a `path` (JSON path to the claim) and optionally `values` (to
              filter by specific claim values) or an `id` (if using `claim_sets`).
            - `claim_sets`: Array of arrays of claim `id`s, specifying preferred combinations of claims.
            - `meta`: Format-specific parameters (e.g., `doctype_value` for `mso_mdoc`, `vct_values` for `dc+sd-jwt`, `type_values` for W3C
              VCs).
            - `trusted_authorities`: To filter by specific issuers or trust frameworks.
            - `require_cryptographic_holder_binding`: Boolean (defaults to `true`).
        - **`credential_sets` array (Optional):**: Defines combinations of *credential query `id`s* that can satisfy the request.
- **Specifying how the Wallet should respond:**
    - **`response_type` (REQUIRED):**
        - `vp_token`: The Wallet will return a `vp_token` containing the presentation(s).
        - `vp_token id_token`: For SIOPv2 integration, returns both `vp_token` and a self-issued `id_token`.
        - `code`: For OAuth 2.0 Authorization Code flow. The Wallet returns a code, which the Verifier exchanges for tokens (including
          `vp_token`) at the Wallet's token endpoint.
    - **`response_mode` (REQUIRED for some `response_type`s, or to override defaults):**
        - `fragment`: (Default for `vp_token` if using redirect) Response parameters in URL fragment. For same-device.
        - `query`: Response parameters in URL query string. Used with `response_type=code`.
        - `direct_post`: Wallet POSTs the response to `response_uri`. For cross-device or large responses.
        - `direct_post.jwt`: Like `direct_post`, but the response is a JWE.
        - `dc_api` / `dc_api.jwt`: When using the Digital Credentials API (Appendix A).
    - **`redirect_uri` (REQUIRED for redirect-based flows):** Where the Wallet redirects the user after processing.
    - **`response_uri` (REQUIRED for `direct_post` modes):** The endpoint where the Wallet POSTs the presentation.
- **Security and State Management:**
    - **`nonce` (REQUIRED):** A fresh, random, unique string for each request. Used to prevent replay attacks and bind the presentation to
      this specific transaction.
    - **`state` (RECOMMENDED, REQUIRED if `require_cryptographic_holder_binding` is false):** An opaque value used by the Verifier to
      maintain session state and protect against CSRF. If requesting presentations without holder binding proofs (Section 5.3), `state` MUST
      be a cryptographically strong random number and validated in the response.

- **Request Delivery Mechanism (for large requests or QR codes):**
    - **`request` (Parameter):** The Authorization Request can be passed by value as a JWT (Request Object).
    - **`request_uri` (Parameter):** A URL pointing to where the Wallet can fetch the full Authorization Request JWT/object. Useful for
      keeping QR codes small.
    - **`request_uri_method` (Section 5.10):** If using `request_uri`, this can be `get` (default) or `post`. `post` allows the Wallet to
      send its metadata (e.g., supported formats) to the Verifier's request object endpoint, enabling the Verifier to tailor the request.
    - **Consideration:** Use `request_uri` for cross-device flows with QR codes. Consider `request_uri_method=post` if you want to
      dynamically adjust the request based on Wallet capabilities. If using `request` or `request_uri` (pointing to a JWT), the JWT *must*
      contain all other Authorization Request parameters (including `dcql_query`, `client_id`, `nonce`, etc.).

- **Advanced/Optional Features:**
    - **`client_metadata` (Section 5.1):** A JSON object for the Verifier to pass its metadata (e.g., `jwks` for response encryption,
      `vp_formats_supported` by the Verifier) if not discoverable via the `client_id` prefix.
    - **`transaction_data` (Section 5.1, 8.4):** Array of base64url-encoded JSON objects to bind the presentation to a specific
      transaction (e.g., payment details). Format-specific handling is required.
    - **`verifier_attestations` (Section 5.1, 5.11):** Array of attestations about the Verifier (e.g., its registration, policies) to help
      the Wallet make trust decisions.
    - **`scope` (Section 5.2, 5.5):** Can be used for pre-defined DCQL queries or for OpenID Connect scopes like `openid` when combining
      with SIOPv2.
    - **Consideration:** Use these to enhance security, provide more context to the Wallet/User, or enable specific use cases like
      transaction authorization.

#### Examples

**Example 1: Simple Same-Device Web Redirect for a W3C Verifiable Credential**

- **Scenario:** User clicks "Verify Age" on a website. Verifier needs a W3C VC proving age over 18.
- **Considerations:**
    * `client_id`: Using `redirect_uri` prefix for simplicity.
    * `response_type`: `vp_token`.
    * `response_mode`: `fragment` (default for this `response_type` with redirect).
    * `dcql_query`: Requesting a specific claim.

- **Verifier constructs this redirect URL (line breaks for readability):**
  ```
  https://wallet.example.com/authorize?
  response_type=vp_token
  &client_id=redirect_uri%3Ahttps%3A%2F%2Fverifier.com%2Fcallback
  &redirect_uri=https%3A%2F%2Fverifier.com%2Fcallback
  &nonce=asdfjkl12345poi098
  &state=verifier_session_abc
  &client_metadata=%7B%22client_name%22%3A%22MyAgeVerifierSite%22%2C%22vp_formats_supported%22%3A%7B%22ldp_vc%22%3A%7B%22proof_type_values%22%3A%5B%22DataIntegrityProof%22%5D%7D%7D%7D
  &dcql_query=%7B%0A%20%20%22credentials%22%3A%20%5B%0A%20%20%20%20%7B%0A%20%20%20%20%20%20%22id%22%3A%20%22age_verification_vc%22%2C%0A%20%20%20%20%20%20%22format%22%3A%20%22ldp_vc%22%2C%0A%20%20%20%20%20%20%22meta%22%3A%20%7B%0A%20%20%20%20%20%20%20%20%22type_values%22%3A%20%5B%5B%22VerifiableCredential%22%2C%20%22AgeCredential%22%5D%5D%0A%20%20%20%20%20%20%7D%2C%0A%20%20%20%20%20%20%22claims%22%3A%20%5B%0A%20%20%20%20%20%20%20%20%7B%20%22path%22%3A%20%5B%22credentialSubject%22%2C%20%22isOver18%22%5D%2C%20%22values%22%3A%20%5Btrue%5D%20%7D%0A%20%20%20%20%20%20%5D%0A%20%20%20%20%7D%0A%20%20%5D%0A%7D
  ```
    * **Decoded `client_metadata`:**
      ```json
      {
        "client_name": "MyAgeVerifierSite",
        "vp_formats_supported": { // Verifier indicates what it can process
          "ldp_vc": {
            "proof_type_values": ["DataIntegrityProof"]
          }
        }
      }
      ```
    * **Decoded `dcql_query`:**
      ```json
      {
        "credentials": [
          {
            "id": "age_verification_vc",
            "format": "ldp_vc",
            "meta": {
              "type_values": [["VerifiableCredential", "AgeCredential"]]
            },
            "claims": [
              { "path": ["credentialSubject", "isOver18"], "values": [true] }
            ]
          }
        ]
      }
      ```

**Example 2: Cross-Device Request for an mdoc Driving License using `request_uri` and `direct_post`**

- **Scenario:** User at a kiosk needs to present their mobile Driving License (mdoc).
- **Considerations:**
    - QR code needs to be small, so `request_uri` is used.
    - Wallet will POST response directly to Verifier.
    - `client_id`: Could be a pre-registered ID for the kiosk.
    - `dcql_query`: Specifies `mso_mdoc` format and `doctype_value`.

- **Step A: Verifier generates QR code content (initial request to Wallet):**
  ```
  openid4vp://authorize?
  client_id=kiosk_verifier_123
  &request_uri=https%3A%2F%2Fverifier.com%2Frequests%2Fmdl_request_abcxyz
  ```

- **Step B: Verifier hosts the Request Object at the `request_uri`:**
    - URL: `https://verifier.com/requests/mdl_request_abcxyz`
    - Content (JWT payload or plain JSON):
      ```json
      {
        "iss": "kiosk_verifier_123", // Issuer of the Request Object
        "aud": "https://self-issued.me/v2", // Audience for the Wallet
        "response_type": "vp_token",
        "response_mode": "direct_post",
        "response_uri": "https://verifier.com/kiosk/presentation_receiver",
        "client_id": "kiosk_verifier_123", // Must match outer client_id
        "nonce": "kiosk_nonce_78910",
        "state": "kiosk_session_def",
        "dcql_query": {
          "credentials": [
            {
              "id": "mdl_query",
              "format": "mso_mdoc",
              "meta": {
                "doctype_value": "org.iso.18013.5.1.mDL" // Specific mdoc type
              },
              "claims": [
                { "id": "fn", "path": ["org.iso.18013.5.1", "family_name"] },
                { "id": "gn", "path": ["org.iso.18013.5.1", "given_name"] },
                { "id": "dob", "path": ["org.iso.18013.5.1", "birth_date"] }
              ],
              "claim_sets": [ // Request all three if possible
                ["fn", "gn", "dob"]
              ]
            }
          ]
        }
      }
      ```

**Example 3: Signed Request Object (JWT) for SD-JWT VC, Combining with SIOPv2**

- **Scenario:** Verifier requires a signed request for higher assurance and also wants to authenticate the user via SIOPv2. Needs an SD-JWT
  VC for membership status.
- **Considerations:**
    - `client_id`: Using `x509_san_dns` prefix, so the request JWT must be signed.
    - `response_type`: `vp_token id_token`.
    - `scope`: `openid` for SIOPv2.
    - `request`: The parameter containing the signed JWT.
    - `dcql_query`: For `dc+sd-jwt` format.

- **Verifier constructs the Authorization Request (this would be the value of the `request` parameter, which itself is a JWT):**
    - **JOSE Header of the Request JWT:**
      ```json
      {
        "alg": "ES256",
        "kid": "verifier_signing_key_id",
        "typ": "oauth-authz-req+jwt", // As per JAR (RFC9101)
        "x5c": ["<verifier_cert_chain_base64...>"] // If using x509_san_dns
      }
      ```
    - **Payload of the Request JWT:**
      ```json
      {
        "iss": "x509_san_dns:secure.verifier.com", // Issuer of the Request Object
        "aud": "https://self-issued.me/v2",      // Audience for the Wallet
        "response_type": "vp_token id_token",
        "response_mode": "direct_post", // Example
        "response_uri": "https://secure.verifier.com/siop_vp_response",
        "client_id": "x509_san_dns:secure.verifier.com", // MUST match 'iss'
        "scope": "openid",
        "nonce": "secure_nonce_45678",
        "state": "high_assurance_session_ghi",
        "id_token_type": "subject_signed", // SIOPv2 parameter
        "dcql_query": {
          "credentials": [
            {
              "id": "membership_card_sdjwt",
              "format": "dc+sd-jwt",
              "meta": {
                "vct_values": ["MembershipCardCredential"] // SD-JWT specific type
              },
              "claims": [
                { "path": ["member_id"] },
                { "path": ["membership_level"] }
              ]
            }
          ]
        },
        "client_metadata": { // Verifier provides its public key for response encryption if needed
          "jwks": {
            "keys": [
              {
                "kty": "EC", "crv": "P-256", "use": "enc", "kid": "verifier_enc_key_1",
                "x": "...", "y": "..."
              }
            ]
          },
          "authorization_encrypted_response_enc": "A256GCM" // Example
        }
      }
      ```
    - The Verifier would then sign this payload with its private key to create the JWT for the `request` parameter. The initial call to the
      Wallet (e.g., via redirect) might look like:
      `https://wallet.example.com/authorize?client_id=x509_san_dns:secure.verifier.com&request=eyJhbGciOiJFUzI1NiI6...`

### Constructing Verifiable Presentation JWT

When the Wallet prepares the `vp_token` to send back, the actual Verifiable Presentation (which is itself a JWT in this jwt_vc_json format)
has to include:
- `aud` (audience claim): value being the Verifiers `client_id`
- `nonce`: value being the `nonce` of the Authorization Request
- `vp` claim containing the actual `verifiableCredential`
- `iss` (issuer claim): value being the DID/identity of the holder/wallet

```json
{
  "iss": "did:example:wallet_holder_did", // Issuer of the Presentation (Holder)
  "aud": "https://proprofile.example.com/openid4vp/client_id", // Verifier's client_id
  "nonce": "a7b3k9zPqR2sT5uV", // Nonce from Authorization Request
  "vp": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiablePresentation"],
    "verifiableCredential": [
      "eyJhbGciOiJFUzI1NksifQ.eyJpc3MiOiJ..." // The OpenBadgeCredential JWT
    ]
  }
  // ... other claims like iat, exp, jti for the Presentation JWT itself
}
```

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
    &
    dcql_query=%7B%22credentials%22%3A%5B%7B%22id%22%3A%22did_auth_query%22%2C%22format%22%3A%22ldp_vc%22%2C%22claims%22%3A%5B%7B%22path%22%3A%5B%22type%22%5D%2C%22values%22%3A%5B%22VerifiableCredential%22%2C%22DIDAuthenticationCredential%22%5D%7D%5D%7D%5D%7D
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
