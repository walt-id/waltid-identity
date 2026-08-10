# walt.id CLI

The CLI manages crypto2 keys and DIDs, issues W3C JWT VCs, verifies digital credentials with policies2, and creates or verifies Final OpenID4VP presentations with DCQL.

## Run

From the unified build root:

```bash
./gradlew :waltid-applications:waltid-cli:runJvm --args='--help'
```

Build a distribution with the CLI project Gradle distribution tasks.

## Keys

`key generate` supports `P-256`, `P-384`, `P-521`, `Ed25519`, and RSA 2048/3072/4096 through the portable crypto2 provider. Selecting `secp256k1` is an explicit JVM-only opt-in backed by the crypto2 Bouncy Castle provider.

Private material is never printed or persisted implicitly. Use `--output` to write a private JWK, or the explicit `--show-private` option to display it. The latter prints a warning and should not be used in captured logs.

```bash
waltid key generate -t P-256 -o issuer.jwk
waltid key generate -t RSA --rsa-bits 3072 -o issuer-rsa.jwk
waltid key generate -t secp256k1 -o issuer-k1.jwk
```

`key convert` uses crypto2 JWK, SPKI, PKCS8, and strict PEM APIs. Input type and key specification are detected automatically. `--key-type` can assert the expected specification. `--output-format` accepts `JWK`, `SPKI`, or `PKCS8`. Private JWK/PKCS8 results require `--output` or explicit `--show-private`; stdout otherwise contains public metadata only.

```bash
waltid key convert -i issuer.jwk -o issuer.pem
waltid key convert -i issuer.jwk -f SPKI -o issuer-public.pem
waltid key convert -i issuer.pem -o issuer.jwk
```

Only canonical `PUBLIC KEY` SPKI PEM and `PRIVATE KEY` PKCS8 PEM are accepted. Encrypted PEM, OpenSSH PEM, SEC1 `EC PRIVATE KEY`, and PKCS1 `RSA PRIVATE KEY` are intentionally rejected. Convert those inputs to unencrypted PKCS8/SPKI before invoking the CLI.

On JVM, secp256k1 generation, conversion, DID registration, VC/VP signing, and CLI VP verification use the Bouncy Castle crypto2 provider. Credential policies2 signer resolution currently uses the portable KMP DID resolver, whose provider profile excludes secp256k1. Consequently `vc verify -p signature` cannot yet verify a secp256k1 DID credential and fails explicitly; it never falls back to crypto1.

## DIDs

Creation uses native `Crypto2DidService` registration.

```bash
waltid did create -m key -k issuer.jwk
waltid did create -m jwk -k issuer.jwk
waltid did resolve -d <did>
```

`did resolve` writes only the resolved JSON document to stdout. CLI creation advertises only the native crypto2 `did:key` and `did:jwk` registrars; former non-native method options were removed.

The published DID library still contains compatibility internals that can transitively reference crypto1 while that library completes its separate migration. The CLI does not duplicate DID resolution and does not import or invoke those legacy key APIs directly: registration and resolution enter through `Crypto2DidService`.

## Credentials

`vc sign` issues a W3C JWT VC with crypto2 and checks that the issuer DID contains the signing key.

```bash
waltid vc sign -k issuer.jwk -s did:example:holder credential.json
```

`vc verify` parses inputs with `CredentialParser`. Signature verification is mandatory. Requested policies are additive and cannot disable it. With no `--policy`, expiration and not-before are also applied. W3C JWT VC, SD-JWT VC, and mdoc signatures are supported by policies2 where their signer keys or certificate chains can be resolved.

```bash
waltid vc verify credential.signed.json
waltid vc verify -p signature -p schema -a schema=schema.json credential.signed.json
```

Other retained policy names are `expiration`/`expired`, `not-before`, `revoked-status-list`, `allowed-issuer`, and `webhook`.

## Presentations

The default and only presentation exchange path is Final OpenID4VP 1.0 DCQL. `vp create` writes the Final `vp_token` JSON object, mapping each DCQL query ID to an array of presentation strings.

```bash
waltid vp create \
  -hd <holder-did> -hk holder.jwk \
  -vd <verifier-client-id> -n <nonce> \
  -vc credential.signed.json \
  -dq query.json -vp vp-token.json

waltid vp verify \
  -hd <holder-did> -vd <verifier-client-id> -n <nonce> \
  -dq query.json -vp vp-token.json
```

Presentation signing uses crypto2. Verification always performs crypto2 envelope and embedded-credential signature validation, current verification-policies2-vp audience/nonce/time checks, DCQL fulfillment, and requested additive policies2 checks.

When a DCQL query has `require_cryptographic_holder_binding=true` (the default), creation and verification reject credentials unrelated to the presenter. SD-JWT uses `cnf`, mdoc uses the MSO device key, and W3C credentials use a holder key when present or exact subject-DID semantics otherwise.

`trusted_authorities` is enforced with the current digital-credentials checker. The CLI explicitly supports `aki`; `etsi_tl`, `openid_federation`, empty values, and unknown authority types fail closed. `multiple=false` rejects ambiguous inputs and requires exactly one returned presentation and credential. Required `credential_sets` must have one complete option, and all references are validated.

The former `--presentation-definition` and `--presentation-submission` options were removed. Final OpenID4VP/DCQL does not define DIF Presentation Definitions or a `presentation_submission`, so those semantics cannot be mapped without retaining a draft-era operational path.

The former `maximum-credentials` and `minimum-credentials` VP policies were also removed because verification-policies2-vp has no equivalent policies. Final requests express cardinality through each DCQL credential query's `multiple` flag and `credential_sets`; translating the old global limits would change their meaning.

`vp create` currently emits `jwt_vc_json`. SD-JWT VC presentations require a credential-specific holder key for KB-JWT binding, and mdoc presentations require a DeviceResponse session transcript. The generic historical holder-key command cannot represent those requirements, so matching those formats fails explicitly instead of producing a non-conformant presentation.

## Launchers

`waltid-cli.sh` and `waltid-cli-development.sh` resolve all paths from their own script location and therefore work from unrelated working directories. `waltid-cli.bat` provides the corresponding build-once Windows entry point. Installed Windows distributions use `%APP_HOME%\lib\*` instead of expanding every JAR into the command line, keeping the launcher below `cmd.exe` length limits.
