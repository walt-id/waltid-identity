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
- 2026-08-26: Added the persisted mdoc holder-key binding field to the
  SQLDelight-generated credential records and queries. No Swift facade shape
  change is needed because `WalletSDK` does not expose those generated
  persistence types; holder-key binding remains automatic internal wallet
  storage behavior.
- 2026-09-30: Kotlin 2.4.20 adds ABI validation for the Android KMP library
  plugin (KT-85950). Added Android JVM baselines for the existing SDK modules;
  added iOS target coverage to the KMS, migration, and JOSE native baselines
  without changing their API declarations. The compiler also adds Java
  no-argument constructors for two defaulted crypto providers (KT-78623).
  Neither change adds a Swift wallet capability, so the existing `WalletSDK`
  facade remains correct.
- 2026-09-30: SQLDelight 2.4.0 adds `allTableNames()` to the generated
  `WalletPersistenceDatabase.Companion`. The Android and native ABI changes are
  additive. No Swift facade change is needed because `WalletSDK` does not expose
  generated persistence types; wallet storage and deletion remain internal.
