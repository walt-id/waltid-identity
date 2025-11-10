<div align="center">
 <h1>Kotlin Multiplatform Crypto library - iOS platform specific</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
  <p>Basic operations over iOS keychain such as signing, verification, export</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://twitter.com/intent/follow?screen_name=walt_id">
<img src="https://img.shields.io/twitter/follow/walt_id.svg?label=Follow%20@walt_id" alt="Follow @walt_id" />
</a>
</div>

## Installation

Add the crypto library as a dependency to your Kotlin or Java project.


```kotlin
dependencies {
  implementation(project(":waltid-libraries:crypto:waltid-crypto-ios"))
}
```

## What is provides

There is sample app that is showcasing iOS crypto library capabilities - `:waltid-applications:waltid-crypto-ios-testApp`. All keys are stored in keychain.

### Currently supported types


| Type  |   ECDSA   | Availability |
|:-----:|:---------:|:-------------|
| EdDSA |  Ed25519  | &check;      |
| ECDSA | secp256r1 | &check;      |
| ECDSA | secp256k1 | &cross;      |
|  RSA  |    RSA    | &check;      |


### Main interface for iOS Keys

```
import id.walt.crypto

...

val key = IosKey.create("kid", KeyType.secp256r1)
// key is id.walt.crypto.keys.Key, use methods and properties as per need


```

#### Implementation details

Module references `:waltid-libraries:waltid-target-ios` that is exposing functionality from static library built by `:waltid-libraries:waltid-target-ios:implementation' module. This module acts as an umbrella for iOS related functionaltiy - for now only crypto things. 

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [Twitter](https://mobile.twitter.com/walt_id)
* Get help, request features and report bugs: [GitHub Issues ](https://github.com/walt-id/waltid-identity/issues)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)
