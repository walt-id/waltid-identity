# 2025.1:

Here's a look at what's new in our latest update! We've been busy aligning with major industry
standards and building powerful new tools to make your life easier.

---

### 🚀 Major Upgrade: Our New OpenID4VP Verifier Service

The digital identity world is buzzing, and for good reason: **OpenID for Verifiable Presentations (
OpenID4VP) 1.0** has officially been released! 🥳

This isn't just a minor update; versions from draft 28 onwards represent a complete overhaul of the
specification. The old approach, which relied on Presentation Exchange (PEX), is gone. The new
standard is now built on the powerful and flexible **Digital Credentials Query Language (DCQL)**.

To support this massive shift, we've built a brand-new **Verifier service** within our Enterprise
stack. This includes a completely re-designed REST API to give you a modern, streamlined experience
for handling verifiable presentations.

#### Get Started

You can create a new verification session by sending a `POST` request to
`/v1/{target}/verifier2-service-api/verification-session/create` with your DCQL query.

```json
{
  "dcql_query": {
    "credentials": [
      {
        "id": "pid",
        "format": "dc+sd-jwt",
        "meta": {
          "vct_values": ["https://org1.enterprise.waltid.cloud/v1/org1.issuer/issuer-service-api/openid4vc/draft13/identity_credential"]
        },
        "claims": [
          { "path": [ "given_name" ] },
          { "path": [ "family_name" ] },
          { "path": [ "address", "street_address" ] }
        ]
      }
    ]
  },
  "policies": {
    "vc_policies": [ "signature", "expiration" ],
    "vp_policies": [ "signature", "holder_binding" ]
  }
}
```

---

### 🔧 New Tool in the Box: waltid-cose for COSE Signatures

We're excited to introduce `waltid-cose`, a simple and powerful new library for handling COSE (CBOR
Object Signing and Encryption). It's perfect for managing credential signatures and is built on our
multiplatform `waltid-crypto` library and `kotlinx-serialization`.

Here's how easy it is to sign and verify data:

#### Sign Data

```kotlin
val signer = key.toCoseSigner() // your key
val signed = CoseSign1.createAndSign(
    protectedHeaders = protectedHeaders,
    unprotectedHeaders = unprotectedHeaders,
    payload = payload,
    signer = signer,
    externalAad = externalAad
)

val signedHex: String = signed.toTagged().toHexString()

println(signedHex) // d28443a10126a1044231315454...
```

#### Verify Signature

```kotlin
val signedHex = "d28443a10126a1044231315454..."

val coseSign1 = CoseSign1.fromTagged(signedHex) // provide signature as hex string or ByteArray

val verifier = key.toCoseVerifier()
val verified: Boolean = coseSelf.verify(verifier, externalAad)

println(verified) // true / false
```

---

### ✅ Trust at Scale: Introducing the VICAL Library

Building on our new COSE library, we're also releasing waltid-vical, a library for issuing and
verifying VICAL (Verified Issuer Certificate Authority Lists). VICAL, defined in the ISO/IEC 18013-5
standard for mobile Driver's Licenses (mDL), provides a standardized way to trust and manage lists
of authorized credential issuers.

Here's a quick example showing how to verify the AAMVA (American Association of Motor Vehicles
Administrators) VICAL and list its allowed issuers.

```kotlin
/* -- Decode the VICAL file -- */
val rawFile: ByteArray = readFile("vicals/aamva.cbor").readBytes()

val vical = Vical.decode(rawFile)

/* -- Verify the VICAL Signature -- */

// 1. Extract the signer's certificate from the VICAL header
val x5Chain = vical.getCertificateChain()
requireNotNull(x5Chain) { "Signer certificate chain (x5chain) not found in header." }
val signerCertificate = x5Chain.first().rawBytes // select a certificate to verify

// 2. Import the certificate as a key that can be used for verification
val signerKey = JWKKey.importFromDerCertificate(signerCertificate).getOrThrow()

// 3. Verify the signature
val isSignatureValid: Boolean = vical.verify(signerKey.toCoseVerifier())
println(isSignatureValid) // true/false

/* -- List allowed issuers -- */
val allowedIssuers = vical.vicalData.getAllAllowedIssuers().entries
vical.vicalData.getAllAllowedIssuers().entries.forEachIndexed { idx, (certificateInfo, certKeyResult) ->
    println("--- ${idx + 1}: Certificate key for: ${certificateInfo.issuingAuthority}")
    val certKey = certKeyResult.getOrNull()
    println("Key: $certKeyResult (${certKey?.getKeyId() ?: "Error"})")
}
println("Allowed issuers per this VICAL: ${allowedIssuers.size}")
```

### 🔐 Custom authentication methods (Enterprise feature)

For on-prem deployments of the Enterprise stacks, you can now configure custom authentication
methods to be used. This feature is based on the multiplatform waltid-ktor-authnz library.

This library provides various authentication methods to choose from besides email/username +
password,
including OIDC, LDAP, RADIUS.

To get started using this feature, edit your `auth.conf` configuration file to set the
authentication flow:

```hocon
# Configure the Auth Flow (refer to: waltid-ktor-authnz)
authFlow = {
    method: radius
    config: {
        radiusServerHost: "localhost"
        radiusServerPort: 1812
        radiusServerSecret: "testing123"
    }
    expiration: "7d" # optional: Set expiration time for login tokens, e.g. a week
    ok: true # Auth flow ends successfuly with this step
}
```

Just like that, users can now authenticate against `POST /auth/account/radius` with their RADIUS
credentials.
