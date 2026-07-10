# Getting Started

Create a ``Wallet`` actor, bootstrap wallet state, and keep the actor as the
native iOS entry point for wallet operations.

## Overview

### Configure a Wallet

Start with a stable ``WalletConfiguration/walletID``. The identifier is used by
the wallet core for persisted local state, so apps should treat it as an
application-level wallet identity rather than as a screen-local value.

```swift
import WalletSDK

let wallet = try await Wallet(
    configuration: WalletConfiguration(
        walletID: "consumer-wallet",
        defaultKeyType: .secp256r1
    )
)
```

> Important: Keep Kotlin Multiplatform and generated bridge symbols behind
> `WalletSDK`. Native iOS consumers should import this Swift package and work
> with Swift-owned types.

### Bootstrap DID State

Call ``Wallet/bootstrap(keyType:didMethod:)`` before issuance or presentation
flows that need wallet key material.

```swift
let bootstrap = try await wallet.bootstrap(didMethod: "key")
print(bootstrap.did)
```

Use the returned ``WalletBootstrapResult/did`` when a verifier flow needs an
explicit wallet DID.
