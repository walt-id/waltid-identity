# WaltidWalletSDKSample

Minimal iOS consumer app for the local `WaltidWalletSDK` Swift package.

This sample exists as a consumer proof for WAL-1065:

- `WaltidWalletSDKSamplePackage` depends on `../../../waltid-libraries/protocols/waltid-wallet-sdk-ios`.
- The feature view imports `WaltidWalletSDK`.
- The local SDK package depends on the generated `WaltidWalletCore.xcframework`.
- UI tests launch the hosted app process, bootstrap the wallet, receive a public EUDI credential, observe SDK events, present it, and wait for verifier backend success.

Build from the identity repo after generating the core XCFramework:

```bash
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:assembleWaltidWalletCoreReleaseXCFramework -PenableIosBuild=true --no-configuration-cache
xcodebuildmcp simulator build --workspace-path waltid-applications/waltid-wallet-sdk-ios-sample/WaltidWalletSDKSample.xcworkspace --scheme WaltidWalletSDKSample --simulator-name "iPhone 17" --configuration Debug
```

Run the hosted runtime proof:

```bash
xcodebuildmcp simulator test --workspace-path waltid-applications/waltid-wallet-sdk-ios-sample/WaltidWalletSDKSample.xcworkspace --scheme WaltidWalletSDKSample --simulator-name "iPhone 17" --configuration Debug --use-latest-os --progress
```
