<div align="center">
<h1>walt.id Wallet SDK - Persistence Layer</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>SQL-backed storage implementations for the walt.id Wallet SDK using Exposed ORM</p>

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

This library provides SQL-backed implementations of the wallet store interfaces defined in [waltid-openid4vc-wallet](../waltid-openid4vc-wallet). It uses JetBrains Exposed ORM with HikariCP connection pooling, supporting SQLite (default) and PostgreSQL databases.

Use this library when you need persistent wallet storage that survives application restarts.

## Features

- **Exposed ORM** — Type-safe SQL with Kotlin DSL
- **HikariCP** — Production-ready connection pooling
- **SQLite** — Zero-configuration default database
- **PostgreSQL** — Production database support
- **Auto-migration** — Automatic schema creation and updates

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven("https://maven.waltid.dev/releases")
}

dependencies {
    implementation("id.walt.protocols:waltid-openid4vc-wallet-persistence:<version>")
    
    // Include PostgreSQL driver if needed
    runtimeOnly("org.postgresql:postgresql:42.7.3")
}
```

## Configuration

### SQLite (Default)

```hocon
wallet2-persistence {
  jdbcUrl = "jdbc:sqlite:wallet2.db"
  driverClassName = "org.sqlite.JDBC"
}
```

### PostgreSQL

```hocon
wallet2-persistence {
  jdbcUrl = "jdbc:postgresql://localhost:5432/wallet2"
  driverClassName = "org.postgresql.Driver"
  username = "wallet"
  password = "secret"
  maximumPoolSize = 10
}
```

## Usage

### Initialize Database

Call `initWallet2Database()` once at application startup:

```kotlin
import id.walt.wallet2.persistence.initWallet2Database
import id.walt.wallet2.persistence.Wallet2PersistenceConfig

// With default SQLite configuration
val db = initWallet2Database()

// With custom configuration
val db = initWallet2Database(
    Wallet2PersistenceConfig(
        jdbcUrl = "jdbc:postgresql://localhost:5432/wallet2",
        driverClassName = "org.postgresql.Driver",
        username = "wallet",
        password = "secret",
        maximumPoolSize = 10
    )
)
```

### Using Exposed Stores

```kotlin
import id.walt.wallet2.persistence.ExposedWalletStore
import id.walt.wallet2.persistence.ExposedKeyStore
import id.walt.wallet2.persistence.ExposedCredentialStore
import id.walt.wallet2.persistence.ExposedDidStore
import id.walt.wallet2.data.Wallet

// Create stores backed by the database
val walletStore = ExposedWalletStore(db)
val keyStore = ExposedKeyStore(db)
val credentialStore = ExposedCredentialStore(db)
val didStore = ExposedDidStore(db)

// Create a wallet using the persistent stores
val wallet = Wallet(
    id = "my-wallet",
    keyStores = listOf(keyStore),
    credentialStores = listOf(credentialStore),
    didStore = didStore
)

// Store the wallet
walletStore.storeWallet(wallet)

// Retrieve later
val loaded = walletStore.resolveWallet("my-wallet")
```

## Store Implementations

| Interface | Implementation | Description |
|-----------|----------------|-------------|
| `WalletStore` | `ExposedWalletStore` | Wallet metadata and store references |
| `WalletKeyStore` | `ExposedKeyStore` | Cryptographic keys (serialized JWK) |
| `WalletCredentialStore` | `ExposedCredentialStore` | Stored credentials with metadata |
| `WalletDidStore` | `ExposedDidStore` | DIDs and their documents |

## Database Schema

The library automatically creates the following tables:

- `wallet2_wallets` — Wallet instances
- `wallet2_keys` — Stored keys
- `wallet2_credentials` — Stored credentials
- `wallet2_dids` — Stored DIDs

Schema migrations are handled automatically by Exposed's `SchemaUtils.createMissingTablesAndColumns()`.

## Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `jdbcUrl` | `jdbc:sqlite:wallet2.db` | JDBC connection URL |
| `driverClassName` | `org.sqlite.JDBC` | JDBC driver class |
| `username` | `""` | Database username |
| `password` | `""` | Database password |
| `maximumPoolSize` | `5` | HikariCP max connections |
| `minimumIdle` | `1` | HikariCP min idle connections |

## Related Libraries

- **[waltid-openid4vc-wallet](../waltid-openid4vc-wallet)** — Core wallet library (store interfaces)
- **[waltid-openid4vc-wallet-server](../waltid-openid4vc-wallet-server)** — HTTP route handlers

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more in-depth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
