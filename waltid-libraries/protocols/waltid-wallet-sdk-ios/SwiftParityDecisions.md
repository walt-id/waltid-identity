# Swift Parity Decisions

Use this file when a Kotlin mobile SDK ABI change has been reviewed for the
Swift facade and intentionally does not need a Swift source, test, README, or
DocC update.

Each entry should name the Kotlin API change, the Swift decision, and the reason
the current Swift facade remains correct.

## Decisions

- 2026-07-07: Added initial Kotlin ABI baselines and explicit API mode for the
  mobile wallet KMP modules. No Swift facade shape change is needed because this
  commit is a contract gate for the existing `WalletSDK` boundary, not a new
  wallet capability.

- 2026-07-13: Extended `DemoWallet` (demo-app interface) with `credentialDetails`,
  `resolveOffer`, and a `txCode: String?` parameter on `receive`; fixed
  `WalletDemoController.resolveAndReceive` to clear the `offerFromDeepLink` flag
  once processing begins.
  Swift decision: No WalletSDK facade update required.
  Reason: All changes are confined to `waltid-wallet-demo-compose` (the demo
  application layer). The `MobileWallet` / `WalletSdkBridge` KMP boundary exposed
  to Swift already carries `txCode` in its `receive` signature and is unaffected;
  `credentialDetails` and `resolveOffer` are demo-app abstractions with no
  corresponding surface in the iOS SDK facade.
