# OpenID4VP Transaction Data Components

This package contains shared transaction-data primitives used by both wallet and verifier paths.

## Shared responsibilities

- `TransactionDataConstants`: protocol constants and default demo type(s)
- `TransactionDataDecoding`: decode base64url transaction_data payloads
- `TransactionDataRequestValidator`: request-side structural and profile checks
- `TransactionDataHashing`: hash algorithm resolution and hash calculation
- `TransactionDataSelection`: credential-id based transaction_data selection
- `MdocTransactionDataConvention`: mdoc device-signed key/index convention

## Module-local responsibilities

- Wallet-side mdoc embedding is implemented as private helpers in `openid4vp-wallet` mdoc presenter
- Verifier policy-side mdoc extraction is implemented as private helpers in `waltid-verification-policies2-vp` mdoc transaction-data policy
- SD-JWT response-side transaction-data hash validation is implemented as private helpers in `waltid-verification-policies2-vp` SD-JWT transaction-data policy

## Holder policy note

No dedicated transaction_data holder policy is added in this refactor.
Current holder policy checks only receive credential streams, while transaction_data validation is request-context dependent.
