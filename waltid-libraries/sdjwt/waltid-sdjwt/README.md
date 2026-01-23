<div align="center">
 <h1>Kotlin Multiplatform SD-JWT library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
  <p>Create JSON Web Tokens (JWTs) that support <b>Selective Disclosure</b></p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
  
  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
    <br/>
    <em>This project is being actively maintained by the development team at walt.id.<br />Regular updates, bug fixes, and new features are being added.</em>
  </p>

</div>

## Getting Started

* [Usage with Maven or Gradle (JVM)](#usage-with-maven-or-gradle-jvm)
* [Usage with NPM/NodeJs (JavaScript)](#usage-with-npmnodejs-javascript)
* [Sign SD-JWT tokens](#create-and-sign-an-sd-jwt-using-the-nimbusds-based-jwt-crypto-provider)
* [Present SD-JWT tokens with selection of disclosed and undisclosed payload fields](#present-an-sd-jwt)
* [Parse and verify SD-JWT tokens, resolving original payload with disclosed fields](#parse-and-verify-an-sd-jwt-using-the-nimbusds-based-jwt-crypto-provider)
* [Integrate with your choice of framework or library, for cryptography and key management, on your platform](#integrate-with-custom-jwt-crypto-provider)

### Further information

Checkout the [documentation regarding SD-JWTs](https://docs.walt.id/concepts/digital-credentials/sd-jwt-vc), to find out more.

## What is the SD-JWT library?

This library implements the **Selective Disclosure JWT (SD-JWT)**
specification:  [draft-ietf-oauth-selective-disclosure-jwt-04](https://datatracker.ietf.org/doc/draft-ietf-oauth-selective-disclosure-jwt/04/) and also provides support for creating, presenting and verifying
verifiable credentials according to the **SD-JWT-VC** specification: 
[SD-JWT-based Verifiable Credentials (SD-JWT VC)](https://www.ietf.org/archive/id/draft-ietf-oauth-sd-jwt-vc-03.html).

### Features

* **Create and sign** SD-JWT tokens
    * Choose selectively disclosable payload fields (SD fields)
    * Create digests for SD fields and insert into JWT body payload
    * Create and append encoded disclosure strings for SD fields to JWT token
    * Add random or fixed number of **decoy digests** on each nested object property
* **Present** SD-JWT tokens
    * Selection of fields to be disclosed
    * Support for appending optional holder binding
* Full support for **nested SD fields** and **recursive disclosures**
* **Parse** SD-JWT tokens and restore original payload with disclosed fields
* **Verify** SD-JWT token
    * Signature verification
    * Hash comparison and tamper check of the appended disclosures
* **SD-JWT-VC** Create, parse, present and verify verifiable credentials according to the SD-JWT-VC specification:
  * https://www.ietf.org/archive/id/draft-ietf-oauth-sd-jwt-vc-03.html
* Support for **integration** with various crypto libraries and frameworks, to perform the cryptographic operations and key management
* **Multiplatform support**:
    * Java/JVM
    * JavaScript
    * Native

## Usage with Maven or Gradle (JVM)

**Maven / Gradle repository**:

Take a look at this build file https://github.com/walt-id/waltid-examples/blob/main/build.gradle.kts , which shows how to use this dependency and other walt.id dependencies.

## Usage with NPM/NodeJs (JavaScript)

**Install NPM package:**

`npm install waltid-sd-jwt`

**Manual build from source:**

`./gradlew jsNodeProductionLibraryPrepare jsNodeProductionLibraryDistribution`

Then include in your NodeJS project like this:

`npm install /path/to/waltid-sd-jwt/build/productionLibrary`

**NodeJS example**

Example script in:

`examples/js`

Execute like:

```bash
npm install
node index.js
```

## Examples

### Kotlin / JVM

#### Create and sign an SD-JWT using the NimbusDS-based JWT crypto provider

This example creates and signs an SD-JWT, using the SimpleJWTCryptoProvider implementation, that's shipped with the waltid-sd-jwt library,
which uses the `nimbus-jose-jwt` library for cryptographic operations.

In this example we sign the JWT with the HS256 algorithm, and a Uuid as a shared secret.

Here we generate the SD payload, by comparing the full payload and the undisclosed payload (with selective fields removed).

Alternatively, we can create the SD payload by specifying the SDMap, which indicates the selective disclosure for each field.
This approach also allows more fine-grained control, particularly in regard to recursive disclosures and nested payload fields.

```kotlin
// Shared secret for HMAC crypto algorithm
val sharedSecret = "ef23f749-7238-481a-815c-f0c2157dfa8e"

fun main() {

  // Create SimpleJWTCryptoProvider with MACSigner and MACVerifier
  val cryptoProvider = SimpleJWTCryptoProvider(JWSAlgorithm.HS256, MACSigner(sharedSecret), MACVerifier(sharedSecret))

  // Create original JWT claims set, using nimbusds claims set builder
  val originalClaimsSet = JWTClaimsSet.Builder()
    .subject("123")
    .audience("456")
    .build()

  // Create undisclosed claims set, by removing e.g. subject property from original claims set
  val undisclosedClaimsSet = JWTClaimsSet.Builder(originalClaimsSet)
    .subject(null)
    .build()

  // Create SD payload by comparing original claims set with undisclosed claims set
  val sdPayload = SDPayload.createSDPayload(originalClaimsSet, undisclosedClaimsSet)
   
  // Create and sign SD-JWT using the generated SD payload and the previously configured crypto provider
  val sdJwt = SDJwt.sign(sdPayload, cryptoProvider)
  // Print SD-JWT
  println(sdJwt)
}
```

_Example output_

``` 
eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0NTYiLCJfc2QiOlsiaGx6ZmpmMDRvNVpzTFIyNWhhNGMtWS05SFcyRFVseGNnaU1ZZDMyNE5nWSJdfQ.2fsLqzujWt0hS0peLS8JLHyyo3D5KCDkNnHcBYqQwVo~WyJ4RFk5VjBtOG43am82ZURIUGtNZ1J3Iiwic3ViIiwiMTIzIl0
```

_Parsed JWT body_

```json
{
  "aud": "456",
  "_sd": [
    "hlzfjf04o5ZsLR25ha4c-Y-9HW2DUlxcgiMYd324NgY"
  ]
}
```

#### Present an SD-JWT

In this example we parse the SD-JWT generated in the previous example, and present it by disclosing all, none or selective fields.

In the next example we will show how to parse and verify the presented SD-JWTs.

```kotlin
fun presentSDJwt() {
  // parse previously created SD-JWT
  val sdJwt = SDJwt.parse("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0NTYiLCJfc2QiOlsiaGx6ZmpmMDRvNVpzTFIyNWhhNGMtWS05SFcyRFVseGNnaU1ZZDMyNE5nWSJdfQ.2fsLqzujWt0hS0peLS8JLHyyo3D5KCDkNnHcBYqQwVo~WyJ4RFk5VjBtOG43am82ZURIUGtNZ1J3Iiwic3ViIiwiMTIzIl0")

  // present without disclosing SD fields
  val presentedUndisclosedJwt = sdJwt.present(discloseAll = false)
  println(presentedUndisclosedJwt)

  // present disclosing all SD fields
  val presentedDisclosedJwt = sdJwt.present(discloseAll = true)
  println(presentedDisclosedJwt)

  // present disclosing selective fields, using SDMap
  val presentedSelectiveJwt = sdJwt.present(mapOf(
    "sub" to SDField(true)
  ).toSDMap())
  println(presentedSelectiveJwt)

  // present disclosing fields, using JSON paths
  val presentedSelectiveJwt2 = sdJwt.present(
    SDMap.generateSDMap(listOf("sub"))
  )
  println(presentedSelectiveJwt2)
  
}
```

_Example output_

```text
eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0NTYiLCJfc2QiOlsiaGx6ZmpmMDRvNVpzTFIyNWhhNGMtWS05SFcyRFVseGNnaU1ZZDMyNE5nWSJdfQ.2fsLqzujWt0hS0peLS8JLHyyo3D5KCDkNnHcBYqQwVo~
eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0NTYiLCJfc2QiOlsiaGx6ZmpmMDRvNVpzTFIyNWhhNGMtWS05SFcyRFVseGNnaU1ZZDMyNE5nWSJdfQ.2fsLqzujWt0hS0peLS8JLHyyo3D5KCDkNnHcBYqQwVo~WyJ4RFk5VjBtOG43am82ZURIUGtNZ1J3Iiwic3ViIiwiMTIzIl0~
eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0NTYiLCJfc2QiOlsiaGx6ZmpmMDRvNVpzTFIyNWhhNGMtWS05SFcyRFVseGNnaU1ZZDMyNE5nWSJdfQ.2fsLqzujWt0hS0peLS8JLHyyo3D5KCDkNnHcBYqQwVo~WyJ4RFk5VjBtOG43am82ZURIUGtNZ1J3Iiwic3ViIiwiMTIzIl0~
eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0NTYiLCJfc2QiOlsiaGx6ZmpmMDRvNVpzTFIyNWhhNGMtWS05SFcyRFVseGNnaU1ZZDMyNE5nWSJdfQ.2fsLqzujWt0hS0peLS8JLHyyo3D5KCDkNnHcBYqQwVo~WyJ4RFk5VjBtOG43am82ZURIUGtNZ1J3Iiwic3ViIiwiMTIzIl0~
```

#### Parse and verify an SD-JWT using the NimbusDS-based JWT crypto provider

This example shows how to parse and verify the SD-JWT, created and presented in the previous examples, and how to restore its original
payload, with the disclosed payload fields only.

For verification, we use the same shared secret as before and a `MACVerifier` with the `SimpleJWTCryptoProvider`.

The parsing and verification can be done in one step using the `SDJwt.verifyAndParse()` method, throwing an exception if verification fails,
or in two steps using the `SDJwt.parse()` method followed by the member method `SDJwt.verify()`, which returns true or false.

The output below shows the restored JWT body payloads, with the selectively disclosable field `sub` disclosed or undisclosed.

```kotlin
// Shared secret for HMAC crypto algorithm
private val sharedSecret = "ef23f749-7238-481a-815c-f0c2157dfa8e"

fun parseAndVerify() {
  // Create SimpleJWTCryptoProvider with MACSigner and MACVerifier
  val cryptoProvider = SimpleJWTCryptoProvider(JWSAlgorithm.HS256, jwsSigner = null, jwsVerifier = MACVerifier(sharedSecret))

  val undisclosedJwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0NTYiLCJfc2QiOlsiaGx6ZmpmMDRvNVpzTFIyNWhhNGMtWS05SFcyRFVseGNnaU1ZZDMyNE5nWSJdfQ.2fsLqzujWt0hS0peLS8JLHyyo3D5KCDkNnHcBYqQwVo~"

  // verify and parse presented SD-JWT with all fields undisclosed, throws Exception if verification fails!
  val parsedVerifiedUndisclosedJwt = SDJwt.verifyAndParse(undisclosedJwt, cryptoProvider)

  // print full payload with disclosed fields only
  println("Undisclosed JWT payload:")
  println(parsedVerifiedUndisclosedJwt.sdPayload.fullPayload.toString())

  // alternatively parse and verify in 2 steps:
  val parsedUndisclosedJwt = SDJwt.parse(undisclosedJwt)
  val isValid = parsedUndisclosedJwt.verify(cryptoProvider)
  println("Undisclosed SD-JWT verified: $isValid")

  val parsedVerifiedDisclosedJwt = SDJwt.verifyAndParse(
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0NTYiLCJfc2QiOlsiaGx6ZmpmMDRvNVpzTFIyNWhhNGMtWS05SFcyRFVseGNnaU1ZZDMyNE5nWSJdfQ.2fsLqzujWt0hS0peLS8JLHyyo3D5KCDkNnHcBYqQwVo~WyJ4RFk5VjBtOG43am82ZURIUGtNZ1J3Iiwic3ViIiwiMTIzIl0~",
    cryptoProvider
  )
  // print full payload with disclosed fields
  println("Disclosed JWT payload:")
  println(parsedVerifiedDisclosedJwt.sdPayload.fullPayload.toString())
}

```

_Example output_

```text
Undisclosed JWT payload:
{"aud":"456"}
Undisclosed SD-JWT verified: true
Disclosed JWT payload:
{"aud":"456","sub":"123"}
```

#### Integrate with custom JWT crypto provider

To integrate with your custom JWT crypto provider, on your platform, you need to override and implement the `JWTCryptoProvider` interface,
which has two interface methods to sign and verify standard JWT tokens.

In this example, you see how I made use of this interface to implement the JWT crypto provider based on the NimbusDS Jose/JWT library for
JVM:

```kotlin
import com.nimbusds.jose.*
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.serialization.json.JsonObject

class SimpleJWTCryptoProvider(
  val jwsAlgorithm: JWSAlgorithm,
  private val jwsSigner: JWSSigner?,
  private val jwsVerifier: JWSVerifier?
) : JWTCryptoProvider {

  /**
   * Interface method to create a signed JWT for the given JSON payload object, with an optional keyID.
   * @param payload The JSON payload of the JWT to be signed
   * @param keyID Optional keyID of the signing key to be used, if required by crypto provider
   */
  override fun sign(payload: JsonObject, keyID: String?): String {
    if(jwsSigner == null) {
      throw Exception("No signer available")
    }
    return SignedJWT(
      JWSHeader.Builder(jwsAlgorithm).type(JOSEObjectType.JWT).keyID(keyID).build(),
      JWTClaimsSet.parse(payload.toString())
    ).also {
      it.sign(jwsSigner)
    }.serialize()
  }

  /**
   * Interface method for verifying a JWT signature
   * @param jwt A signed JWT token to be verified
   */
  override fun verify(jwt: String): Boolean {
    if(jwsVerifier == null) {
      throw Exception("No verifier available")
    }
    return SignedJWT.parse(jwt).verify(jwsVerifier)
  }
}
```

The custom JWT crypto provider can now be used like shown in the examples above,
for [signing](#create-and-sign-an-sd-jwt-using-the-nimbusds-based-jwt-crypto-provider)
and [verifying](#parse-and-verify-an-sd-jwt-using-the-nimbusds-based-jwt-crypto-provider) SD-JWTs.

### JavaScript / NodeJS

See also example project in `examples/js`

**Build payload, sign and present examples**

```javascript
import sdlib from "waltid-sd-jwt"

const sharedSecret = "ef23f749-7238-481a-815c-f0c2157dfa8e"
const cryptoProvider = new sdlib.id.walt.sdjwt.SimpleAsyncJWTCryptoProvider("HS256", new TextEncoder().encode(sharedSecret))

const sdMap = new sdlib.id.walt.sdjwt.SDMapBuilder(sdlib.id.walt.sdjwt.DecoyMode.FIXED.name, 2).addField("sub", true,
    new sdlib.id.walt.sdjwt.SDMapBuilder().addField("child", true).buildAsJSON()
).buildAsJSON()

console.log(sdMap, JSON.stringify(sdMap))

const sdPayload = new sdlib.id.walt.sdjwt.SDPayloadBuilder({"sub": "123", "aud": "345"}).buildForUndisclosedPayload({"aud": "345"})
const sdPayload2 = new sdlib.id.walt.sdjwt.SDPayloadBuilder({"sub": "123", "aud": "345"}).buildForSDMap(sdMap)

const jwt = await sdlib.id.walt.sdjwt.SDJwtJS.Companion.signAsync(
    sdPayload, cryptoProvider)
console.log(jwt.toString())

const jwt2 = await sdlib.id.walt.sdjwt.SDJwtJS.Companion.signAsync(
    sdPayload2, cryptoProvider)
console.log(jwt2.toString())

console.log("Verified:", (await jwt.verifyAsync(cryptoProvider)).verified)
console.log("Verified:", (await jwt2.verifyAsync(cryptoProvider)).verified)

const presentedJwt = await jwt.presentAllAsync(false)
console.log("Presented undisclosed SD-JWT:", presentedJwt.toString())
console.log("Verified: ", (await presentedJwt.verifyAsync(cryptoProvider)).verified)

const sdMap2 = new sdlib.id.walt.sdjwt.SDMapBuilder().buildFromJsonPaths(["sub"])
console.log("SDMap2:", sdMap2)
const presentedJwt2 = await jwt.presentAsync(sdMap2)
console.log("Presented disclosed SD-JWT:", presentedJwt2.toString())
const verificationResultPresentedJwt2 = await presentedJwt2.verifyAsync(cryptoProvider)
console.log("Presented payload", verificationResultPresentedJwt2.sdJwt.fullPayload)
console.log("Presented disclosures", verificationResultPresentedJwt2.sdJwt.disclosureObjects)
console.log("Presented disclosure strings", verificationResultPresentedJwt2.sdJwt.disclosures)
console.log("Verified: ", verificationResultPresentedJwt2.verified)
console.log("SDMap reconstructed", presentedJwt2.sdMap)

```

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
