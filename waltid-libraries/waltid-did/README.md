<div align="center">
 <h1>Kotlin Multiplatform DID library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
  <p>Create, register, and resolve Decentralized Identifiers (DIDs) on different ecosystems</p>

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

## What is the did library
This library implements the did specification: [W3C Decentralized Identifiers (DIDs)](https://www.w3.org/TR/did-core/).

### Further information

Checkout the [documentation regarding decentralized identifiers](https://docs.walt.id/concepts/decentralised-identifiers), to find out more.

## Installation

Add the did library as a dependency to your Kotlin or Java project, which includes the crypto lib.

### walt.id Repository

Add the Maven repository which hosts the walt.id libraries to your build.gradle file.

```kotlin
repositories {
    maven { url = uri("https://maven.waltid.dev/releases") }
} 
```

### Library Dependency

Adding the did library as dependency. Specify the version that coincides with the latest or required
snapshot for your project. [Latest releases](https://github.com/walt-id/waltid-identity/releases).

```kotlin
dependencies {
  implementation("id.walt.did:waltid-did:<version>")
}
```

Replace `version` with the version of the walt.id did library you want to use.
Note: As the did lib is part of the mono-repo walt.id identity, you need to use the version of
walt.id identity.

## What it provides

_**walt.id did**_ library provides functionality for registering and resolving
Decentralized Identifiers (DIDs).
There are 2 options available for each function:

- local - provides local implementations of DID methods in order to resolve DIDs in a decentralized way
- remote - relies on the universal DID registrar / resolver, e.g.:
    - uni-registrar - https://uniregistrar.io
    - uni-resolver - https://dev.uniresolver.io

For the cryptographic part, _**walt.id did**_ library relies on _**walt.id crypto**_ library.

The top-level interface to access the registrar / resolver functions is provided
by the `DidService` singleton.
The complete class hierarchy can be viewed in the [class diagram](did-lib_class.drawio.png).

## How to use it

### Local Resolver & Registrar Setup
This function initializes the DidService instance with the local DID registrars and resolvers.
```kotlin
DidService.minimalInit()
```
### Standard Setup 

You can leverage the universal registrar and resolver by suing the `init()` method for setup.
```kotlin
DidService.init()
```

### Register DID

#### Single Key

Create the key and register the DID:

```kotlin
val options = DidWebCreateOptions(
    domain = "example.com",
    path = "/path/to/did.json",
    keyType = KeyType.Ed25519
)
val didResult = DidService.register(options = options)
```

Register the DID with a given key:

```kotlin
val key = JWKKey.generate(KeyType.Ed25519)
val options = DidKeyCreateOptions(
    useJwkJcsPub = true
)
val didResult = DidService.registerByKey(
    method = "key",
    key = key,
    options = options
)
```

#### DID Document Configuration
Register a DID by having more fine-grained control over the contents of the DID Document that will be produced. This approach allows users to specify multiple keys (verification methods) for different purposes (verification relationships), define services and various use-case-specific (custom) properties.

```kotlin
val publicKeySet = setOf(
  JWKKey.generate(KeyType.RSA).getPublicKey(),
  JWKKey.generate(KeyType.secp256k1).getPublicKey(),
  JWKKey.generate(KeyType.secp256r1).getPublicKey(),
)
val didDocConfig = DidDocConfig.buildFromPublicKeySet(
  publicKeySet = publicKeySet,
)
var webCreateOptions = DidWebCreateOptions(
  domain = "wallet.walt-test.cloud",
  path = "/wallet-api/registry/1111",
  didDocConfig = didDocConfig,
)
val didResult = DidService.register(webCreateOptions)
```
For more information on the capabilities provided with this approach, refer to the documentation of the `DidDocConfig` class and its respective methods, as well as, our [waltid-examples](https://github.com/walt-id/waltid-examples) project.

**Note:** Support for this feature is currently limited to the `did:web` method.

#### Result

All calls return a `DidResult` object:

```kotlin
data class DidResult(
    val did: String,
    val didDocument: DidDocument
)
```

where `did` - is the DID url string, while `didDocument` is the corresponding
DidDocument represented as a key-value pair, having the key as a `String` and
value as a `JsonElement`.

### Resolve DID

Resolve the DID url to a DID Document:

```kotlin
val didDocumentResult = DidService.resolve("did:web:example.com")
val document = didDocumentResult.getOrNull()
```

Resolve the DID url to its public Key:

```kotlin
val did =
    "did:key:zmYg9bgKmRiCqTTd9MA1ufVE9tfzUptwQp4GMRxptXquJWw4Uj5cqKBi2vyiwwxC3v7ixvJ8SB9DvDdrK7UemySWDPhvHhUcZ7pgtZtFchLtzK4YC"
val keyResult = DidService.resolveToKey(did = did)
val key = keyResult.getOrNull()
```

Both calls return the result using the _operation result pattern_,
the data being wrapped by the `Result` object. This allows checking for
a successful operation and handling the result accordingly.

The DID Document data is represented as `JsonObject`. The key data is
represented as **_walt.id crypto_** `Key`.

## Local DID operations implemented natively

<table>
    <tbody>
        <!-- header -->
        <tr>
            <td align="center" rowspan="2">Method</td>
            <td align="center" rowspan="2">Key</td>
            <td align="center" colspan="3">Feature</td>
            <td align="center" rowspan="2">DID Document<br>Configuration</td>
        </tr>
        <!-- function sub-header -->
        <tr>
            <td align="center" >create</td>
            <td align="center" >register</td>
            <td align="center" >resolve</td>
        </tr>
        <!-- content -->
        <!-- key -->
        <!-- ed25519 -->
        <tr>
            <td align="center" rowspan="4">key*</td>
            <td align="center" >ed25519</td>
            <td align="center" >&check;</td>
            <td align="center" >&dash;</td>
            <td align="center" >&check;</td>
            <td align="center" rowspan="4">&dash;</td>
        </tr>
        <!-- secp256k1 -->
        <tr>
            <td align="center" >secp256k1</td>
            <td align="center" >&check;</td>
            <td align="center" >&dash;</td>
            <td align="center" >&check;</td>
        </tr>
        <!-- secp256r1 -->
        <tr>
            <td align="center" >secp256r1</td>
            <td align="center" >&check;</td>
            <td align="center" >&dash;</td>
            <td align="center" >&check;</td>
        </tr>
        <!-- rsa -->
        <tr>
            <td align="center" >rsa</td>
            <td align="center" >&check;</td>
            <td align="center" >&dash;</td>
            <td align="center" >&check;</td>
        </tr>
        <!-- end key -->
        <tr><td colspan="6"></td></tr>
        <!-- jwk -->
        <!-- ed25519 -->
        <tr>
            <td align="center" rowspan="4">jwk</td>
            <td align="center" >ed25519</td>
            <td align="center" >&check;</td>
            <td align="center" >&dash;</td>
            <td align="center" >&check;</td>
            <td align="center" rowspan="4">&dash;</td>
        </tr>
        <!-- secp256k1 -->
        <tr>
            <td align="center" >secp256k1</td>
            <td align="center" >&check;</td>
            <td align="center" >&dash;</td>
            <td align="center" >&check;</td>
        </tr>
        <!-- secp256r1 -->
        <tr>
            <td align="center" >secp256r1</td>
            <td align="center" >&check;</td>
            <td align="center" >&dash;</td>
            <td align="center" >&check;</td>
        </tr>
        <!-- rsa -->
        <tr>
            <td align="center" >rsa</td>
            <td align="center" >&check;</td>
            <td align="center" >&dash;</td>
            <td align="center" >&check;</td>
        </tr>
        <!-- end jwk -->
        <tr><td colspan="5"></td></tr>
        <!-- web -->
        <!-- ed25519 -->
        <tr>
            <td align="center" rowspan="4">web</td>
            <td align="center" >ed25519</td>
            <td align="center" >&check;</td>
            <td align="center" >&cross;</td>
            <td align="center" >&check;</td>
            <td align="center" rowspan="4">&check;</td>
        </tr>
        <!-- secp256k1 -->
        <tr>
            <td align="center" >secp256k1</td>
            <td align="center" >&check;</td>
            <td align="center" >&cross;</td>
            <td align="center" >&check;</td>
        </tr>
        <!-- secp256r1 -->
        <tr>
            <td align="center" >secp256r1</td>
            <td align="center" >&check;</td>
            <td align="center" >&cross;</td>
            <td align="center" >&check;</td>
        </tr>
        <!-- rsa -->
        <tr>
            <td align="center" >rsa</td>
            <td align="center" >&check;</td>
            <td align="center" >&cross;</td>
            <td align="center" >&check;</td>
        </tr>
        <!-- end web -->
        <tr><td colspan="5"></td></tr>
        <!-- cheqd -->
        <!-- ed25519 -->
        <tr>
            <td align="center" >cheqd</td>
            <td align="center" >ed25519</td>
            <td align="center" >&check;</td>
            <td align="center" >&check;</td>
            <td align="center" >&check;</td>
            <td align="center" >&cross;</td>
        </tr>
        <!-- end cheqd -->
        <tr><td colspan="5"></td></tr>
        <!-- ebsi -->
        <tr>
            <td align="center" rowspan="2">ebsi</td>
            <td align="center" >secp256r1</td>
            <td align="center" >&cross;</td>
            <td align="center" >&cross;</td>
            <td align="center" >&check;</td>
            <td align="center" rowspan="2">&cross;</td>
        </tr>
        <tr>
            <td align="center" >secp256k1</td>
            <td align="center" >&cross;</td>
            <td align="center" >&cross;</td>
            <td align="center" >&check;</td>
        </tr>
        <!-- end ebsi -->
        <tr><td colspan="5"></td></tr>
        <!-- iota -->
        <tr>
            <td align="center" >iota</td>
            <td align="center" >ed25519</td>
            <td align="center" >&cross;</td>
            <td align="center" >&cross;</td>
            <td align="center" >&cross;</td>
            <td align="center" >&cross;</td>
        </tr>
        <!-- end iota -->
    </tbody>
</table>

- &check; implemented
- &cross; not implemented
- &dash; not available

(*) The did:key implementation defaults to W3C CCG https://w3c-ccg.github.io/did-method-key/. By setting _useJwkJcsPub_ to `true` the EBSI
implementation (jwk_jcs-pub encoding) according https://hub.ebsi.eu/tools/libraries/key-did-resolver is performed.

## Remote DID operations by 3rd party services (fallback)

According Universal Resolver: https://github.com/decentralized-identity/universal-resolver/

According Universal Registrar: https://github.com/decentralized-identity/universal-registrar/


## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)
<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
