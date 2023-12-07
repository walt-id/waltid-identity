<div align="center">
 <h1>Kotlin Multiplatform DID library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>

</p>
<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://twitter.com/intent/follow?screen_name=walt_id">
<img src="https://img.shields.io/twitter/follow/walt_id.svg?label=Follow%20@walt_id" alt="Follow @walt_id" />
</a>


</div>

## What it provides

_**walt.id did**_ library provides functionality for registering and resolving
Decentralized Identifiers (DIDs).
There are 2 options available for each function:

- universal - relies on the universal DID registrar / resolver, e.g.:
    - uni-registrar - https://uniregistrar.io
    - uni-resolver - https://dev.uniresolver.io
- local - provides local implementations of DID methods

For the cryptographic part, _**walt.id did**_ library relies on _**walt.id crypto**_ library.

The top-level interface to access the registrar / resolver functions is provided
by the `DidService` singleton.
The complete class hierarchy can be viewed in the [class diagram](did-lib_class.drawio.png).

## How to use it

### Register DID

Create the key and register the Did:

```kotlin
val options = DidWebCreateOptions(
    domain = "localhost:3000",
    path = "/path/to/did.json",
    keyType = KeyType.Ed25519
)
val didResult = DidService.register(options = options)
```

Register the Did with a given key:

```kotlin
val key = LocalKey.generate(KeyType.Ed25519)
val options = DidKeyCreateOptions(
    useJwkJcsPub = true
)
val didResult = DidService.register(
    method = "key",
    key = key,
    options = options
)
```

Both calls return a `DidResult` object:

```kotlin
data class DidResult(
    val did: String,
    val didDocument: DidDocument
)
```

where `did` - is the Did url string, while `didDocument` is the corresponding
DidDocument represented as a key-value pair, having the key as a `String` and
value as a `JsonElement`.

### Resolve DID

Resolve the Did url to a Did Document:

```kotlin
val did = "did:web:localhost:3000"
val didDocumentResult = DidService.resolve(did = did)
val document = didDocumentResult.getOrNull()
```

Resolve the Did url to its public Key:

```kotlin
val did =
    "did:key:zmYg9bgKmRiCqTTd9MA1ufVE9tfzUptwQp4GMRxptXquJWw4Uj5cqKBi2vyiwwxC3v7ixvJ8SB9DvDdrK7UemySWDPhvHhUcZ7pgtZtFchLtzK4YC"
val keyResult = DidService.resolveToKey(did = did)
val key = keyResult.getOrNull()
```

Both calls return the result using the _operation result pattern_,
the data being wrapped by the `Result` object. This allows checking for
a successful operation and handling the result accordingly.

The Did Document data is represented as `JsonObject`. The key data is
represented as **_walt.id crypto_** `Key`.

## Local DID operations implemented natively

<table>
    <tbody>
        <!-- header -->
        <tr>
            <td align="center" rowspan="2">Method</td>
            <td align="center" rowspan="2">Key</td>
            <td align="center" colspan="3">Feature</td>
        </tr>
        <!-- function sub-header -->
        <tr>
            <td align="center">create</td>
            <td align="center">register</td>
            <td align="center">resolve</td>
        </tr>
        <!-- content -->
        <!-- key -->
        <!-- ed25519 -->
        <tr>
            <td align="center" rowspan="4">key</td>
            <td align="center">ed25519</td>
            <td align="center">&check;</td>
            <td align="center">&dash;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- secp256k1 -->
        <tr>
            <td align="center">secp256k1</td>
            <td align="center">&check;</td>
            <td align="center">&dash;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- secp256r1 -->
        <tr>
            <td align="center">secp256r1</td>
            <td align="center">&check;</td>
            <td align="center">&dash;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- rsa -->
        <tr>
            <td align="center">rsa</td>
            <td align="center">&check;</td>
            <td align="center">&dash;</td>
            <td align="center">&cross;</td>
        </tr>
        <!-- end key -->
        <tr><td colspan="5"></td></tr>
        <!-- jwk -->
        <!-- ed25519 -->
        <tr>
            <td align="center" rowspan="4">jwk</td>
            <td align="center">ed25519</td>
            <td align="center">&check;</td>
            <td align="center">&dash;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- secp256k1 -->
        <tr>
            <td align="center">secp256k1</td>
            <td align="center">&check;</td>
            <td align="center">&dash;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- secp256r1 -->
        <tr>
            <td align="center">secp256r1</td>
            <td align="center">&check;</td>
            <td align="center">&dash;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- rsa -->
        <tr>
            <td align="center">rsa</td>
            <td align="center">&check;</td>
            <td align="center">&dash;</td>
            <td align="center">&cross;</td>
        </tr>
        <!-- end jwk -->
        <tr><td colspan="5"></td></tr>
        <!-- web -->
        <!-- ed25519 -->
        <tr>
            <td align="center" rowspan="4">web</td>
            <td align="center">ed25519</td>
            <td align="center">&check;</td>
            <td align="center">&cross;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- secp256k1 -->
        <tr>
            <td align="center">secp256k1</td>
            <td align="center">&check;</td>
            <td align="center">&cross;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- secp256r1 -->
        <tr>
            <td align="center">secp256r1</td>
            <td align="center">&check;</td>
            <td align="center">&cross;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- rsa -->
        <tr>
            <td align="center">rsa</td>
            <td align="center">&check;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
        </tr>
        <!-- end web -->
        <tr><td colspan="5"></td></tr>
        <!-- cheqd -->
        <!-- ed25519 -->
        <tr>
            <td align="center">cheqd</td>
            <td align="center">ed25519</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- end cheqd -->
        <tr><td colspan="5"></td></tr>
        <!-- ebsi -->
        <tr>
            <td align="center">ebsi</td>
            <td align="center">secp256r1</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
        </tr>
        <!-- end ebsi -->
        <tr><td colspan="5"></td></tr>
        <!-- iota -->
        <tr>
            <td align="center">iota</td>
            <td align="center">ed25519</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
            <td align="center">&cross;</td>
        </tr>
        <!-- end iota -->
    </tbody>
</table>

## Remote DID operations by 3d party services (fallback)

<table>
    <tbody>
        <!-- header -->
        <tr>
            <td align="center" rowspan="2">Method</td>
            <td align="center" rowspan="2">Key</td>
            <td align="center" colspan="2">Feature</td>
        </tr>
        <!-- function sub-header -->
        <tr>
            <td align="center">create</td>
            <td align="center">resolve</td>
        </tr>
        <!-- content -->
        <!-- ed25519 -->
        <tr>
            <td align="center" rowspan="4">key</td>
            <td align="center">ed25519</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- secp256k1 -->
        <tr>
            <td align="center">secp256k1</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- secp256r1 -->
        <tr>
            <td align="center">secp256r1</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- rsa -->
        <tr>
            <td align="center">rsa</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- end key -->
        <tr><td colspan="4"></td></tr>
        <!-- jwk -->
        <!-- ed25519 -->
        <tr>
            <td align="center" rowspan="4">jwk</td>
            <td align="center">ed25519</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- secp256k1 -->
        <tr>
            <td align="center">secp256k1</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- secp256r1 -->
        <tr>
            <td align="center">secp256r1</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- rsa -->
        <tr>
            <td align="center">rsa</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- end jwk -->
        <tr><td colspan="4"></td></tr>
        <!-- web -->
        <tr>
            <td align="center" rowspan="4">web</td>
            <td align="center">ed25519</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- secp256k1 -->
        <tr>
            <td align="center">secp256k1</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- secp256r1 -->
        <tr>
            <td align="center">secp256r1</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- rsa -->
        <tr>
            <td align="center">rsa</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- end web -->
        <tr><td colspan="4"></td></tr>
        <!-- cheqd -->
        <tr>
            <td align="center">cheqd</td>
            <td align="center">ed25519</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <tr><td colspan="4"></td></tr>
        <!-- ebsi -->
        <tr>
            <td align="center">ebsi</td>
            <td align="center">secp256r1</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <tr><td colspan="4"></td></tr>
        <!-- iota -->
        <tr>
            <td align="center">iota</td>
            <td align="center">ed25519</td>
            <td align="center">&check;</td>
            <td align="center">&check;</td>
        </tr>
        <!-- end iota -->
    </tbody>
</table>