<div align="center">
    <h1>walt.id OpenID4VC iOS Test App</h1>
    <span>by </span><a href="https://walt.id">walt.id</a>
    <p>iOS test application demonstrating OpenID for Verifiable Credentials (OID4VCI and OID4VP) on iOS</p>
    <a href="https://walt.id/community">
    <img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
    </a>
    <a href="https://www.linkedin.com/company/walt-id/">
    <img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
    </a>
    <h2>Status</h2>
    <p align="center">
        <img src="https://img.shields.io/badge/🟡%20Unmaintained-yellow?style=for-the-badge&logo=warning" alt="Status: Unmaintained" />
        <br/>
        <em>This project is not actively maintained. Certain features may be outdated or not working as expected.<br />We encourage users to contribute to the project to help keep it up to date.</em>
    </p>

</div>

## What This Application Contains

`waltid-openid4vc-ios-testApp` is an iOS test application that demonstrates OpenID for Verifiable Credentials (OpenID4VC) functionality on iOS. It provides a reference implementation for testing credential issuance (OID4VCI) and presentation (OID4VP) flows using Kotlin Multiplatform libraries compiled to iOS.

## Main Purpose

This test application enables developers to:

- **Test OpenID4VC on iOS**: Verify that OpenID4VC libraries work correctly on iOS platforms
- **Demonstrate Credential Issuance**: Test accepting credential offers and receiving verifiable credentials
- **Demonstrate Credential Presentation**: Test processing presentation requests and authorizing presentations
- **Validate iOS Integration**: Ensure multiplatform Kotlin libraries integrate properly with iOS Keychain and native iOS APIs

## Key Concepts

### OpenID4VC on iOS

The application uses Kotlin Multiplatform to share code between platforms:

- **Shared Kotlin Code**: Core OpenID4VC logic written in Kotlin and compiled to iOS
- **iOS Keychain Integration**: Uses `waltid-crypto-ios` for secure key storage in iOS Keychain
- **Native Swift UI**: SwiftUI interface for user interaction
- **Protocol Support**: Implements OID4VCI (Credential Issuance) and OID4VP (Presentation) flows

### Credential Issuance Flow (OID4VCI)

1. **Generate Key**: Create a cryptographic key in iOS Keychain
2. **Receive Offer**: Accept a credential offer URI (from issuer portal)
3. **Process Offer**: Parse and validate the credential offer
4. **Request Credential**: Exchange authorization for credential token
5. **Receive Credential**: Obtain the verifiable credential

### Credential Presentation Flow (OID4VP)

1. **Receive Presentation Request**: Accept a presentation request URI (from verifier)
2. **Authorize Presentation**: Process the authorization request
3. **Build Presentation**: Create a verifiable presentation with selected credentials
4. **Submit Presentation**: Return the presentation to the verifier

## Assumptions and Dependencies

### Platform Support

- **iOS 15.4+**: Requires iOS 15.4 or later
- **Xcode**: Requires Xcode for building and running
- **CocoaPods**: Uses CocoaPods for dependency management
- **Kotlin Multiplatform**: Uses Kotlin Multiplatform for shared code

### Dependencies

- **waltid-openid4vc**: OpenID4VC protocol implementation (Kotlin Multiplatform)
- **waltid-crypto-ios**: iOS Keychain integration for cryptographic keys
- **waltid-w3c-credentials**: W3C Verifiable Credentials support
- **waltid-did**: Decentralized Identifier support
- **waltid-sdjwt**: SD-JWT credential support
- **JOSESwift**: JOSE library for iOS (via CocoaPods)
- **Ktor Client**: HTTP client for protocol communication

## Usage

### Prerequisites

- Xcode 14.0 or later
- iOS 15.4+ device or simulator
- CocoaPods installed (`sudo gem install cocoapods`)

### Setup

1. **Install CocoaPods dependencies**:

```bash
cd iosApp
pod install
```

2. **Generate Kotlin framework** (if needed):

```bash
cd ../shared
../../gradlew :waltid-applications:waltid-openid4vc-ios-testApp:shared:generateDummyFramework
```

3. **Open the workspace**:

```bash
open iosApp.xcworkspace
```

4. **Build and run** in Xcode:
   - Select a target device or simulator
   - Press ⌘R to build and run

### Using the Application

1. **Generate a Key**:
   - Enter or generate a random key ID (kid)
   - Tap "Generate key" to create a key in iOS Keychain

2. **Test Credential Issuance**:
   - Browse to https://portal.walt.id
   - Generate a credential offer (without PIN)
   - Paste the credential offer URI into the app
   - Tap "Process credential offer" to accept and receive the credential

3. **Test Credential Presentation**:
   - Get a presentation request URI from a verifier
   - Paste the presentation offer URI into the app
   - Tap "Process presentation offer" to authorize and create the presentation

### Key Components

- **Platform.ios.kt**: iOS-specific platform code for key generation and wallet setup
- **Wallet.kt**: OpenID4VC wallet implementation using shared Kotlin libraries
- **ContentView.swift**: SwiftUI interface for testing flows

## Related Libraries

- **[waltid-openid4vc](../waltid-libraries/protocols/waltid-openid4vc)**: Core OpenID4VC protocol implementation
- **[waltid-crypto-ios](../waltid-libraries/crypto/waltid-crypto-ios)**: iOS Keychain integration
- **[waltid-w3c-credentials](../waltid-libraries/credentials/waltid-w3c-credentials)**: W3C credential support
- **[waltid-did](../waltid-libraries/waltid-did)**: DID support

## Testing

This application is primarily used for:

- **Integration Testing**: Verifying OpenID4VC libraries work on iOS
- **Protocol Validation**: Testing OID4VCI and OID4VP flows end-to-end
- **Keychain Integration**: Validating iOS Keychain key storage and operations
- **Reference Implementation**: Providing examples for iOS developers

## Development Notes

- The shared Kotlin code is compiled to an iOS framework
- CocoaPods manages the integration between Swift and Kotlin
- Keys are stored securely in iOS Keychain
- The app uses async/await patterns for Swift-Kotlin interop

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>

