# Mobile Wallet SDK Integration

Create a wallet, receive credentials, list stored credentials, and present to a
verifier from Swift UI code.

## Create the Bridge

`WalletDemoBridgeController` is the demo app's Swift-facing wrapper around the
Kotlin Multiplatform SDK. Keep one controller per persisted wallet identifier.

```swift
let controller = WalletDemoBridgeController(walletId: "default")
```

Use a stable wallet identifier when you want credentials, DIDs, and key
references to persist across controller recreation and app launches.

## Bootstrap the Wallet

Bootstrap creates or reuses platform-backed signing material and a DID.

```swift
let result = try await controller.bootstrap()

if result.success {
    print("Wallet DID: \(result.message)")
}
```

On iOS, the SDK factory uses Keychain/Secure Enclave key storage through the
mobile persistence module.

## Receive a Credential

Pass an OpenID4VCI credential offer URL to the bridge.

```swift
let result = try await controller.receiveCredential(offerUrl)
```

The SDK stores accepted credentials in the mobile SQLDelight database and keeps
signing key material in platform storage.

## List Credentials

Use the bridge from a Swift view model and publish the returned summaries to the
UI.

```swift
let credentials = try await controller.listCredentials()
```

Each `BridgeCredential` is a display summary derived from the SDK credential
metadata.

## Present a Credential

Pass an OpenID4VP authorization request URL to the bridge.

```swift
let result = try await controller.presentCredential(requestUrl)
```

The SDK selects matching credentials, creates the presentation response, and
returns whether transmission to the verifier succeeded.

## Configure Client Attestation

Some enterprise issuer deployments require OAuth 2.0 client attestation. Provide
the attester settings when creating the bridge.

```swift
let controller = WalletDemoBridgeController(
    walletId: "default",
    attestationBaseUrl: "https://enterprise.example.com",
    attestationAttesterPath: "/client-attester",
    attestationBearerToken: token,
    attestationHostHeader: nil
)
```

Use `attestationHostHeader` only for tunneled local enterprise setups where the
public URL and upstream host differ.
