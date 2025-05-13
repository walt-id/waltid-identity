# Verifier `client_id`

## What's up with `client_id`? Identifying the Verifier.

At its core, `client_id` is how the Wallet identifies who is making the request (the Verifier).

In traditional OAuth 2.0, clients (Verifiers) are often pre-registered with Authorization Servers (Wallets, in this context).
The `client_id` would be an identifier assigned during that registration.

## Why Prefixes? Dynamic Trust and Metadata.

OpenID4VP aims for more dynamic interactions where a Wallet might not have pre-registered every possible Verifier.
Client Identifier Prefixes provide standardized ways for the Wallet to:

- **Authenticate the Verifier**: Verify that the request is genuinely from the claimed Verifier.
- **Obtain Verifier Metadata**: Get information about the Verifier (its name, logo, public keys for encryption, supported formats, etc.)
  without prior registration.

## Common Prefixes

### No Prefix

e.g., `client_id="my_verifier_app_123"`

- **Meaning:** The Wallet is expected to know this `client_id` from a previous registration or configuration. Metadata is obtained through
  that pre-established relationship.
- **Use Case:** Closed ecosystems or when Verifiers register with Wallet providers.

### `redirect_uri:{your_redirect_uri}`

e.g., `client_id="redirect_uri:https://verifier.com/callback"`

- **Meaning:** The Verifier's identity is tied to its `redirect_uri`. The Wallet trusts that if it sends the response to this
  `redirect_uri`, it's going to the correct Verifier.
- **Metadata:** The Verifier *must* pass its metadata using the `client_metadata` parameter in the Authorization Request.
- **Security:** This method **cannot be used for signed Authorization Requests** because there's no inherent cryptographic way for the
  Wallet to verify the *source* of the request based solely on the `redirect_uri` as the identifier. It relies on the security of the
  `redirect_uri` itself.
- **Use Case:** Simpler setups, same-device flows where request signing isn't critical, or when dynamic metadata provision is needed without
  complex PKI.

### `x509_san_dns:{dns_name}`

e.g., `client_id="x509_san_dns:secure.verifier.com"`

- **Meaning:** The Verifier will sign the Authorization Request (which will be a JWT Request Object). The Wallet verifies this signature
  using a public key from an X.509 certificate. The `dNSName` Subject Alternative Name (SAN) in that certificate *must* match `{dns_name}`.
  The Wallet also needs to trust the certificate's issuer (CA).
- **Metadata:** Can be passed via `client_metadata` or potentially discovered via other means associated with the certificate/domain.
- **Use Case:** Higher assurance, when Verifiers have domain-validated X.509 certificates and need to prove their identity
  cryptographically.

### `decentralized_identifier:{did_string}`

e.g., `client_id="decentralized_identifier:did:example:12345"`

- **Meaning:** The Verifier signs the Authorization Request using the private key associated with the specified Decentralized Identifier (
  DID). The Wallet resolves the DID Document to get the public key for verification.
- **Metadata:** Can be passed via `client_metadata` or often found in the DID Document's service endpoints.
- **Use Case:** Verifiers using Self-Sovereign Identity (SSI) principles with DIDs.

### `verifier_attestation:{subject_in_attestation}`

e.g., `client_id="verifier_attestation:verifier.example.com"`

- **Meaning:** The Verifier includes a "Verifier Attestation JWT" in the header of its signed Authorization Request. This attestation is
  issued by a party the Wallet trusts, and it confirms the Verifier's identity and its public key. The `client_id` value must match the
  `sub` (subject) claim in the Verifier Attestation JWT.
- **Metadata:** Can be passed via `client_metadata` or potentially included in the attestation.
- **Use Case:** When Verifiers are part of a trust framework where an attestation issuer vouches for them.

### `openid_federation:{entity_id}`

e.g., `client_id="openid_federation:https://verifier.example.com/entity_statement"`

- **Meaning:** The Verifier's identity and metadata are resolved using the OpenID Federation protocol. The Wallet fetches the Verifier's
  entity statement from the `{entity_id}` URL and validates the trust chain back to a trusted anchor.
- **Metadata:** Obtained directly from the federation trust chain. `client_metadata` in the request is usually ignored.
- **Use Case:** Verifiers participating in an OpenID Federation.
