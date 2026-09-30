# JWT key attestations

`DefaultCredentialProofVerifier` supports a `key_attestation` JWT in the protected JOSE header of each `proofs.jwt` entry. The outer holder signature and the trusted attester signature are verified separately. The holder signing key must appear in the attestation's `attested_keys`, compared by public JWK thumbprint.

Configure key-attestation trust separately from OAuth client authentication. Trusting an OAuth client attester does not authorize it to attest credential keys. Attestation acquisition is a wallet/provider integration; this library does not define an acquisition endpoint.

## Configuration

Both issuer2 implementations accept `keyAttestationConfig`. Enterprise resolves `key-reference` through the issuer's KMS dependencies. The OSS module accepts a `KeyAttestationKeyReferenceResolver` when embedded; a standalone deployment without that resolver should use `static-jwk` or `x509-chain`.

```json
{
  "keyAttestationConfig": {
    "verificationMethod": {
      "type": "key-reference",
      "reference": "organization.tenant.kms.key-attester"
    },
    "limits": {
      "maxAttestedKeys": 32,
      "maxCredentials": 32,
      "maxJwtLength": 65536
    }
  }
}
```

Other verification methods are `{"type":"static-jwk","jwk":{...}}`, containing a public asymmetric verification JWK, and `{"type":"x509-chain","trustedRootCertificatesPem":["-----BEGIN CERTIFICATE-----\n..."]}`. X.509 verification requires one leaf-first `x5c` chain to a configured root and verifies the signature with that exact leaf. Ambiguous subjects, repeated certificates, and unused chain entries are rejected. It does not fetch token-supplied URLs or inherit OS trust roots.

To require attestations, use the existing credential-configuration metadata:

```json
{
  "proof_types_supported": {
    "jwt": {
      "proof_signing_alg_values_supported": ["ES256"],
      "key_attestations_required": {}
    }
  }
}
```

An empty requirements object still requires evidence. Omitting it makes evidence optional; any supplied evidence is still fully verified. Both signing algorithms must be advertised. Optional `key_storage` and `user_authentication` requirements accept exact matches against their configured sets. Hosts reject required-attestation configurations without trust material.

### EUDI reference environment

The OSS issuer2 sample `config/issuer-service.conf` configures key-attestation X.509 trust using PID Issuer CA 02 (EU), the CA used by the EUDI reference Wallet Providers. Its SHA-256 certificate fingerprint is `3B:0A:22:3E:87:48:F3:C4:22:17:29:8A:D9:05:EC:F1:A1:04:E7:3C:D2:C5:4F:F2:1D:A1:6F:DA:80:70:B2:97`. Adding this trust does not make attestations mandatory; set the metadata above on the credentials that require them.

Enterprise Swagger includes **Issuer2 - EUDI Required JWT Key Attestation**, using the same CA for separate key-attestation and OAuth client-attestation configurations. Its SD-JWT credential requires `proofs.jwt` with `key_attestation` and advertises ES256. Set the example's service/KMS/token-key references for your deployment.

These are reference-environment test settings. A CA trust anchor accepts qualifying chains from that CA; it does not pin a specific Wallet Provider identity. To pin one signer instead, use `static-jwk` with that provider's public signing JWK and update it on rotation. The issuer does not fetch or refresh JWKS automatically. The [EUDI Wallet Provider documentation](https://github.com/eu-digital-identity-wallet/eudi-srv-wallet-provider) describes metadata discovery at `/.well-known/oauth-protected-resource`; the demo and dev providers are `https://wallet-provider.eudiw.dev` and `https://dev.wallet-provider.eudiw.dev`. The X.509 configuration requires the attestation JWT to carry the signer's `x5c` chain.

## Request shape

The HTTP body still carries the outer compact JWT:

```json
{
  "credential_configuration_id": "identity_credential",
  "proofs": { "jwt": ["<holder-signed compact JWT>"] }
}
```

Its protected header contains the holder key and the complete inner compact JWT:

```json
{
  "typ": "openid4vci-proof+jwt",
  "alg": "ES256",
  "jwk": { "kty": "EC", "crv": "P-256", "x": "...", "y": "..." },
  "key_attestation": "<attester-signed compact JWT>"
}
```

Put the holder's public JWK and any additional public holder keys in the inner payload's `attested_keys` array. The attester signing key is configured separately; it is not a holder binding. Replace the placeholders with real keys and signatures; these snippets illustrate structure only.

## Library integration

```kotlin
val context = CredentialProofValidationContext(
    credentialIssuer = issuerIdentifier,
    nonceValidation = nonceValidation,
    keyAttestation = keyAttestationConfig.toVerificationOptions(keyReferenceResolver),
)
```

For custom trust, supply `KeyAttestationVerificationOptions` with a `KeyAttestationTrustResolver`. It must return only keys authorized to attest credential keys for the supplied issuer and credential configuration, with exportable public material. Verification uses a local software key restored from that public material; it does not invoke a remote verifier. Public-key retrieval failures are issuer service errors. Token headers are untrusted lookup hints. A custom `KeyAttestationPolicy` receives verified evidence for additional certification/status checks. Built-in validation checks claim structure but does not perform revocation lookup or certification verification. Deployments requiring those checks must supply that policy.

The inner JWT requires `typ=key-attestation+jwt`, an asymmetric signature, integer `iat` and `exp`, and a nonempty public `attested_keys` array. An optional `nbf` must be a numeric date and must not be in the future beyond the configured clock skew. When nonce validation is configured, both JWTs must carry valid issuer nonces. Validation preserves the nonce service's reusable-until-expiry behavior. The inner JWT does not inherit the outer proof's five-minute maximum age, nor OAuth client-attestation claims such as `cnf` or `sub`.

Malformed, untrusted, expired, or mismatched evidence produces `invalid_proof`; missing or invalid required nonces produce `invalid_nonce`. Trust/policy infrastructure failures throw `KeyAttestationServiceException`, which the issuer maps to its server-error path. Custom resolvers should return an empty list for an unknown signer and throw for operational failures.

## Multi-key issuance and API migration

`CredentialProofVerifier.verify` returns `CredentialProofVerificationResult`:

- `proofs`: one verified evidence record for each submitted JWT, retaining its one signing key and optional verified attestation.
- `bindings`: the ordered keys selected for credential issuance, including their associated proof indexes and any verified DID identifiers.

For attested requests, all distinct attested keys are selected in first-occurrence order. Overlaps across proofs are deduplicated while all submitted evidence is verified. Plain-JWT-only requests retain their previous duplicate-key behavior. Mixed optional attested/plain proofs use the same distinct-key selection. Each selected binding receives a separate issuance input and credential/status allocation.

`batch_credential_issuance.batch_size` limits submitted proofs. `maxAttestedKeys` limits each attestation before key restoration; `maxCredentials` limits the combined selection before input allocation. Exceeding a limit rejects the complete request; keys are not silently truncated.

SD-JWT VC and mdoc can bind additional keys through JWK/COSE. Extra keys never inherit the outer signer's DID. DID-only configurations and W3C JWT credential handlers require a verified DID for each selected key; otherwise the request fails before issuance inputs are allocated.

Embedding code implementing a custom verifier must migrate from a list of proofs to `CredentialProofVerificationResult`. Custom credential handlers now receive `CredentialIssuanceBatch.bindings` and `CredentialIssuanceInstance.verifiedBinding`; direct signer calls use `verifiedBinding`. Use `VerifiedCredentialProof.binding(index)` for ordinary one-key proofs. Compare evidence count with submitted proof count, and allocate credentials using binding count.

Standalone `proofs.attestation`, `di_vp`, Federation trust chains, and outer-proof `x5c` resolution remain unsupported. The reusable inner verifier and evidence/binding split provide the extension points for later carriers.

Protocol reference: [OpenID4VCI 1.0 final](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-final.html), Appendices D.1 and F.1.
