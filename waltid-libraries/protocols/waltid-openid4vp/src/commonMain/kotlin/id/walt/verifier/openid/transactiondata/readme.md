# OpenID4VP Transaction Data Components

This package contains shared transaction-data primitives used by both wallet and verifier paths.

## Shared responsibilities

- `TransactionDataDecoding`: decode base64url transaction_data items into `DecodedTransactionData` (parsed JSON + typed `TransactionDataItem`)
- `TransactionDataRequestValidator`: structural checks (non-blank type, non-empty credential_ids, holder binding requirement, supported formats) and optional type-registry enforcement
- `TransactionDataHashing`: hash algorithm resolution (`sha-256` only for now) and hash calculation
- `TransactionDataSelection`: filters transaction_data items by credential_id (`filterTransactionDataForCredentialId`)
- `TransactionDataTypeRegistry`: set of known type strings; `requireKnown` rejects unrecognized types

## Validation modes

Two entry points in `TransactionDataRequestValidator`:

- `validateRequestTransactionData(transactionData, typeRegistry, credentialQueriesById)` — used by the **wallet**. Enforces both structural rules and that each type is in the registry.
- `validateRequestTransactionDataStructure(transactionData, credentialQueriesById)` — used by the **verifier** when creating sessions. Runs structural checks only (no type registry).

Both validate:
- `type` is non-blank
- `credential_ids` is non-empty and references valid DCQL credential query ids (when provided)
- Referenced credential queries use a supported format (`dc+sd-jwt` or `mso_mdoc`)
- Referenced credential queries require cryptographic holder binding
- `require_cryptographic_holder_binding` is not explicitly `false`
- `transaction_data_hashes_alg` values are supported (when present)

## mdoc convention

Per OID4VP (section 8.4), each transaction data type maps to its own namespace in DeviceSigned.
The wallet embeds:
- `transaction_data_hash` (CBOR byte string) — SHA-256 of the base64url-encoded transaction_data item
- `transaction_data_hash_alg` (string, optional) — included when `transaction_data_hashes_alg` is present in the request

One transaction data item per type per credential presentation is supported.

The credential's `keyAuthorizations` must authorize either the namespace or the individual element identifiers.

## Service-layer configuration

Concrete type sets and UI metadata (display names, field lists for discovery endpoints) live in the service layer
(`waltid-wallet-api`, `waltid-verifier-api2`) via `TransactionDataProfilesConfig`. This protocol module only knows type strings.

## Module-local responsibilities

- Wallet-side mdoc embedding: `MdocPresenter` in `waltid-openid4vp-wallet`
- Wallet-side SD-JWT KB-JWT hashes: `WalletPresentFunctionality2` in `waltid-openid4vp-wallet`
- Verifier-side session creation validation: `VerificationSessionCreator` in `waltid-openid4vp-verifier`
- Verifier policy (mdoc hash check): `TransactionDataMdocVpPolicy` in `waltid-verification-policies2-vp`
- Verifier policy (SD-JWT hash check): SD-JWT transaction-data policy in `waltid-verification-policies2-vp`
