Verifier Hosts Request Object at `request_uri`
(e.g. `https://api.example.com/openid4vp/requests/f47ac10b-58cc-4372-a567-0e02b2c3d479`)

Content (served as application/oauth-authz-req+jwt or application/json if unsigned):

```json
{
  "iss": "https://proprofile.example.com/openid4vp/client_id", // Issuer of the Request Object
  "aud": "https://self-issued.me/v2", // Standard audience for Wallet
  "response_type": "vp_token",
  "response_mode": "direct_post",
  "response_uri": "https://api.proprofile.example.com/openid4vp/presentation_response",
  "client_id": "https://proprofile.example.com/openid4vp/client_id",
  "nonce": "a7b3k9zPqR2sT5uV",
  "state": "session_verifier_badge_add_112233",
  "dcql_query": {
    "credentials": [
      {
        "id": "conference_badge_2025",
        "format": "jwt_vc_json",
        "meta": {
          "type_values": [ // W3C VC specific meta
            ["VerifiableCredential", "OpenBadgeCredential"]
          ]
        },
        "claims": [
          { "path": ["credentialSubject", "name"] }, // Name of the badge
          { "path": ["credentialSubject", "description"] },
          { "path": ["issuer", "name"] } // Name of the badge issuer
        ]
      }
    ]
  },
  "client_metadata": { // Optional: Verifier's capabilities
    "client_name": "ProProfile Badge Verifier",
    "logo_uri": "https://proprofile.example.com/logo.png",
    "vp_formats_supported": { // What the Verifier can process
        "jwt_vc_json": { "alg_values": ["ES256K", "EdDSA"] }
    }
  }
}
```

- `application/json`: **Unsigned**
- `application/oauth-authz-req+jwt`: **signed JWT** (as per JAR - JWT Secured Authorization Request, RFC 9101)
  - To do so:
    - Construct all the standard OpenID4VP Authorization Request parameters as a JSON object
      - This will become the **payload** of the JWT
    - Crucial Claims within this payload:
      - iss (Issuer): REQUIRED. This claim MUST be the Verifier's `client_id` (the one the Wallet will use to identify the Verifier and potentially discover its keys).
      - aud (Audience): REQUIRED. This claim MUST be a value that identifies the Wallet as the intended audience. Often, this is https://self-issued.me/v2 (as per SIOPv2, which OpenID4VP often aligns with for this) or could be the Wallet's issuer identifier if known.
      - All other OpenID4VP parameters: client_id (again, matching iss), response_type, response_mode, response_uri (or redirect_uri), nonce, state (optional), dcql_query, scope (optional), client_metadata (optional), etc.
