# OpenID4VP Transaction Data Components

This package contains shared transaction-data primitives used by both wallet and verifier paths.

## Shared responsibilities

- `TransactionDataDecoding`: decode base64url transaction_data payloads
- `TransactionDataRequestValidator`: request-side structural and profile checks
- `TransactionDataHashing`: hash algorithm resolution and hash calculation
- `TransactionDataSelection`: credential-id based transaction_data selection

## Type profiles (`profile/` subpackage)

Each transaction data type (e.g. `org.waltid.transaction-data.payment-authorization`) is represented by a
`TransactionDataTypeProfile` that defines:

- Applicability to credential formats/docTypes
- The mdoc response namespace (convention: the type identifier itself)
- Extra elements to include in DeviceSigned beyond the framework-level hash binding
- Optional per-type validation
- A human-readable display name for wallet UI

The `TransactionDataTypeProfileRegistry` holds known profiles. When non-empty, it enforces that all
transaction data types in a request are registered (wallet use case). When empty (default), structural
checks run but type validation is skipped (verifier use case).

Concrete profiles are defined in the service layer (e.g. `waltid-wallet-api`), not in this protocol module.

Files:
- `profile/TransactionDataTypeProfile.kt`: abstract base class
- `profile/TransactionDataTypeProfileRegistry.kt`: registry

## mdoc convention

Per the OID4VP spec each transaction data type maps to its own namespace in DeviceSigned.
The wallet embeds `transaction_data_hash` (CBOR byte string) and optionally `transaction_data_hash_alg`
(string) into the type's namespace. The verifier extracts and verifies these bindings.

One transaction data item per type per credential presentation is supported.

## Module-local responsibilities

- Wallet-side mdoc embedding: private helpers in `openid4vp-wallet` MdocPresenter
- Verifier policy mdoc extraction: private helpers in `waltid-verification-policies2-vp` TransactionDataMdocVpPolicy
- SD-JWT KB-JWT hashes: handled in `openid4vp-wallet` WalletPresentFunctionality2 and verified by the SD-JWT transaction-data policy
