<div align="center">
<h1>walt.id Wallet Migration Tool</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Migrate data from waltid-wallet-api (v1) to the new Wallet SDK schema</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
  
  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
    <br/>
    <em>This project is being actively maintained by the development team at walt.id.<br />Regular updates, bug fixes, and new features are being added.</em>
  </p>
</div>

## Overview

This tool migrates wallet data from the legacy `waltid-wallet-api` (v1) database schema to the new Wallet SDK schema used by `waltid-openid4vc-wallet-persistence`. It handles the conversion of wallets, keys, credentials, DIDs, and account mappings.

## Features

- **Idempotent** — Safe to run multiple times; won't duplicate data
- **Dry-run mode** — Preview what would be migrated without writing
- **Multi-database support** — SQLite and PostgreSQL for both source and target
- **Credential re-parsing** — Converts old credential format to new `DigitalCredential` serialization
- **Error resilience** — Continues migration on individual item failures, reports errors at end

## Usage

### Basic Migration (SQLite)

```bash
./gradlew :waltid-services:waltid-wallet-migration:run \
  --args="--source jdbc:sqlite:/path/to/old/wallet.db --target jdbc:sqlite:/path/to/new/wallet2.db"
```

### PostgreSQL Migration

```bash
./gradlew :waltid-services:waltid-wallet-migration:run \
  --args="--source 'jdbc:postgresql://localhost:5432/walletv1?user=wallet&password=secret' --target 'jdbc:postgresql://localhost:5432/walletv2?user=wallet&password=secret'"
```

### Dry Run (Preview Only)

Add `--dry-run` to see what would be migrated without making any changes:

```bash
./gradlew :waltid-services:waltid-wallet-migration:run \
  --args="--source jdbc:sqlite:wallet.db --target jdbc:sqlite:wallet2.db --dry-run"
```

## Migration Mapping

| Old Table (v1) | New Table(s) (v2) |
|----------------|-------------------|
| `wallets` | `wallet2_wallets` + store registration + junction tables |
| `wallet_keys` | `wallet2_keys` (per-wallet key store) |
| `credentials` | `wallet2_credentials` (re-serialized as `DigitalCredential`) |
| `wallet_dids` | `wallet2_dids` (document string → JsonObject) |
| `account_wallet_mapping` | `wallet2_account_wallets` |

## Store Creation

Each migrated wallet gets exactly one store of each type with deterministic IDs:

| Store Type | ID Pattern |
|------------|------------|
| Key Store | `wallet-{uuid}-keys` |
| Credential Store | `wallet-{uuid}-creds` |
| DID Store | `wallet-{uuid}-dids` |

## Command-Line Options

| Option | Required | Description |
|--------|----------|-------------|
| `--source <jdbc-url>` | Yes | JDBC URL for the source (v1) database |
| `--target <jdbc-url>` | Yes | JDBC URL for the target (v2) database |
| `--dry-run` | No | Preview migration without writing |

## Output

The tool logs progress and provides a summary at completion:

```
═══════════════════════════════════════════
  Migration complete
  Wallets:          42
  Keys:             156
  Credentials:      89
  DIDs:             42
  Account mappings: 42
═══════════════════════════════════════════
```

If any items fail to migrate, warnings are logged during the run and an error count is shown in the summary.

## Supported Databases

| Database | Source | Target |
|----------|--------|--------|
| SQLite | ✅ | ✅ |
| PostgreSQL | ✅ | ✅ |

The JDBC driver is automatically inferred from the URL. Both source and target can use different database types (e.g., migrate from SQLite to PostgreSQL).

## Prerequisites

- Source database must be accessible and contain the v1 schema
- Target database will be initialized automatically (tables created if missing)
- For PostgreSQL, ensure the database exists before running

## Related Libraries

- **[waltid-openid4vc-wallet-persistence](../../waltid-libraries/protocols/waltid-openid4vc-wallet-persistence)** — Target schema and store implementations
- **[waltid-wallet-api](../waltid-wallet-api)** — Legacy wallet service (source schema)

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more in-depth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
