# waltid-crypto2-pkcs11

JVM PKCS#11 managed-key provider for `waltid-crypto2`, on top of JCA/SunPKCS11. Private keys never
leave the token: PINs are resolved per token and are never serialized with a key.

Supports EC (P-256/384/521) and RSA signing - ECDSA, RSASSA-PKCS1-v1_5 and RSASSA-PSS - persistent
aliases, adoption of pre-provisioned keys, and deletion.

## Vendor neutrality

No vendor API is used. Everything that differs between tokens is resolved from the token itself
rather than assumed:

| Concern                  | How it is handled                                                                                                                                                                                                             |
|--------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Token addressing         | `slotId` (`CK_SLOT_ID`) or `slotListIndex`, both native SunPKCS11 directives. Prefer `slotId`: it is what an HSM operator has and is unaffected by other tokens appearing.                                                    |
| Which algorithms exist   | Probed from the provider's registered `Signature`/`KeyPairGenerator` services, which SunPKCS11 derives from the token's `C_GetMechanismList`. A token without RSA-PSS, or with fewer digests, advertises exactly what it has. |
| ECDSA mechanism          | Always raw `CKM_ECDSA` with the digest computed on this side. It is the one ECDSA mechanism every token implements.                                                                                                           |
| Generated-key attributes | The hardening template is **best-effort**: a token that rejects it is retried without one, and the resulting key's safety properties are then verified behaviourally.                                                         |
| Verification             | Runs in software against the pinned public key. It needs no private key, so the token is never involved.                                                                                                                      |
| RSA certificate digest   | Falls back through SHA-256/384/512 to whatever the token registers.                                                                                                                                                           |
| Vendor-only requirements | `providerConfigurationLines` appends verbatim SunPKCS11 configuration. Newlines are rejected so a value cannot inject unrelated directives.                                                                                   |

Verified against **SoftHSMv2** and a **real TPM 2.0** (AMD firmware TPM via tpm2-pkcs11 1.10).
`VendorTokenSmokeTest` runs the identical code path against a real token and is how a portability
claim gets checked rather than asserted:

```bash
# Thales Luna
WALTID_PKCS11_LIBRARY=/usr/safenet/lunaclient/lib/libCryptoki2_64.so \
WALTID_PKCS11_SLOT_ID=0 WALTID_PKCS11_PIN=... \
  ./gradlew :waltid-libraries:crypto:waltid-crypto2-pkcs11:test --tests '*VendorTokenSmokeTest*'

# TPM 2.0 via tpm2-pkcs11
WALTID_PKCS11_LIBRARY=/usr/lib/x86_64-linux-gnu/libtpm2_pkcs11.so \
WALTID_PKCS11_SLOT_ID=1 WALTID_PKCS11_PIN=... ./gradlew ... 
```

The smoke test signs and verifies with **every** algorithm the token advertises, so an
over-advertising library fails the test rather than failing in production.

### Measured vendor differences, all handled

Each of these was found by running against a real token, not predicted:

| Observation                                                                                                                                               | Token                  | Consequence                                                                                                                             |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| `SHA256withECDSA` and `SHA256withECDSAinP1363Format` are registered, and signing with either fails at `C_SignUpdate` with `CKR_OPERATION_NOT_INITIALIZED` | SoftHSMv2              | Service registration is *not* proof a combined ECDSA mechanism works. Only raw `CKM_ECDSA` is used.                                     |
| A `generate` attribute template containing **any** attribute is rejected (`CKR_GENERAL_ERROR` / `CKR_ATTRIBUTE_VALUE_INVALID`)                            | tpm2-pkcs11            | The template is best-effort; the token's session is downgraded once and the key's properties are verified instead.                      |
| Without the template, the generated **RSA private key is decrypt-capable**                                                                                | SoftHSMv2              | This is the RSAES-PKCS1-v1_5 padding oracle the template exists to prevent - so it is now verified on the key, not assumed from config. |
| RSA key generation fails outright (`CKR_GENERAL_ERROR`); EC P-256/P-384 only, no P-521                                                                    | AMD fTPM               | Advertisement is probed, so callers see what the token can do. RSA generation fails with the token's own error.                         |
| Verifying with the certificate's (software) public key through the *token* silently returns `false`, because the token cannot import it                   | tpm2-pkcs11            | Verification moved to software against the pinned public key. Also removes an HSM round trip.                                           |
| Token sits at `CK_SLOT_ID` 1, not 0                                                                                                                       | tpm2-pkcs11            | Addressing by slot ID matters; list-index-only addressing was fragile.                                                                  |
| `C_Logout` on one provider instance deauthenticates every other instance's sessions **for that slot**                                                     | SoftHSMv2, tpm2-pkcs11 | Logout only when the last holder of the token releases it, counting both template variants.                                             |
| A second instance accepts a **wrong PIN** once the token is logged in (`CKR_USER_ALREADY_LOGGED_IN`)                                                      | SoftHSMv2              | `KeyStore.load` cannot be used to validate a PIN. Documented rather than asserted.                                                      |

## Sessions and login state

One logged-in session per token, shared process-wide and reference-counted (`Pkcs11Sessions`). Three
PKCS#11 properties force this, and they hold for every vendor:

1. A `SunPKCS11` instance cannot be terminated, and `Provider.configure` returns a *new* instance
   with its own token handle and session pool. Configuring per operation leaks a token plus its
   sessions on every signature and repeats `C_Login`, which real tokens answer with
   `CKR_SESSION_COUNT` or `CKR_DEVICE_MEMORY`.
2. Login state is per application (process) per slot. `C_Logout` from any instance deauthenticates
   every other instance's sessions for that slot. Logout therefore happens only when the last holder
   releases the token.
3. For the same reason **`KeyStore.load(null, pin)` cannot be relied on to validate a PIN**: once
   the token is logged in, `C_Login` returns `CKR_USER_ALREADY_LOGGED_IN` and a wrong PIN is
   accepted. A wrong PIN is only detected on the first login to a token within a process.

Call `close()` on the provider at shutdown so the token is logged out.

## Adopting a key the operator provisioned

The usual HSM workflow: the key is created by whoever administers the token, often under a key
ceremony with dual control, and the application receives only an alias and a PIN.

```kotlin
val descriptor = provider.storedKeyForExisting(
    id = KeyId("issuer-signing-key"),
    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    options = Pkcs11Options(
        libraryPath = "/usr/safenet/lunaclient/lib/libCryptoki2_64.so",
        pinReference = "luna-partition",
        slotId = 0,
        alias = "issuer-signing-key",
    ),
)
```

The key specification is read from the token, not supplied by the caller, so a descriptor cannot
disagree with the key it points at. The read also proves the alias exists, has a usable certificate,
and that the PIN is correct, before anything is persisted. Keys without a certificate are not
addressable: SunPKCS11's `KeyStore` only exposes a private key that has one.

## Generated key attributes, and why they are verified rather than trusted

The requested template is `CKA_TOKEN`, `CKA_PRIVATE`, `CKA_SENSITIVE`, `CKA_EXTRACTABLE = false`,
`CKA_SIGN = true`, plus explicitly `CKA_DECRYPT`, `CKA_UNWRAP`, `CKA_SIGN_RECOVER`,
`CKA_DERIVE = false`.

The negative attributes matter, and this is measured rather than argued: on SoftHSM **without** them
the generated RSA private key *is* decrypt-capable, i.e. an RSAES-PKCS1-v1_5 padding oracle for any
PKCS#11 client holding the PIN.

But a template is only a request, and tpm2-pkcs11 rejects one outright. So after generation the two
properties that matter are checked on the key the token actually produced, and generation fails with
the key rolled back if either does not hold:

- the private key must not be extractable (`getEncoded() == null`);
- an RSA private key must not permit decryption.

Config was never proof that a template applied. On a TPM the template never applies, and the key is
safe anyway because the hardware cannot do otherwise - the verification is what establishes that, on
any vendor.

Public keys are generated as **session** objects. SunPKCS11 reads the public key from the
certificate, so a token public-key object serves no purpose, and `P11KeyStore.deleteEntry` destroys
only the private key and the certificate - a persistent one would be orphaned on the token by every
generate/delete cycle.

## Deliberately unsupported

RSA encryption and RSA key wrapping. SunPKCS11 registers only `RSA/ECB/PKCS1Padding` and
`RSA/ECB/NoPadding` (`P11RSACipher` rejects every OAEP padding and `CKM_RSA_PKCS_OAEP` is never
mapped), so RSA-OAEP is unreachable through the JCA `Cipher` API for any token, and
`RSAES-PKCS1-v1_5` decryption is a Bleichenbacher padding oracle against a token-held key. Key
usages are restricted to `SIGN` and `VERIFY`. If wrapping is ever required it should be added as
AES-KW under an OAEP-capable mechanism, not PKCS#1 v1.5.

Ed25519 and secp256k1 are not offered: SunPKCS11 support is inconsistent across tokens.

A key without a certificate is not addressable, because SunPKCS11's `KeyStore` only exposes a
private key that has one. This was the main risk to the design on a TPM, where object storage is
scarce - it was checked, and `CKO_CERTIFICATE` objects work on the AMD fTPM.
