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
