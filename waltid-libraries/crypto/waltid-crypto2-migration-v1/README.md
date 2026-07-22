# waltid-crypto2-migration-v1

One-way migration from persisted `waltid-crypto` v1 keys to crypto2 `StoredKey` records. Local JWK and mobile software keys migrate offline. Remote and platform records require an explicitly registered provider-aware migrator. There is intentionally no crypto2-to-v1 conversion.
