<div align="center">
 <h1>Wallet API</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Identity wallets to manage Keys, DIDs, Credentials, and NFTs/SBTs</p>

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

Watch the [Intro Video](https://www.youtube.com/watch?v=ILaSAxjoHbw&t=1s) to learn about features and see a demo.
The [documentation](https://docs.walt.id/community-stack/wallet/getting-started) explains how to create and manage identity
wallets.

# What it provides

The Wallet-API is designed to provide a broad range of API endpoints that let you offer identity wallets to users
capable of handling different keys, DIDs, and credential types and facilitate the receipt and presentation of
credentials from various issuers and verifiers using the OIDC4VC protocol standard. Alongside digital identity
capabilities, it also supports the integration of web3 wallets. This feature enables your users to view their tokens
from different blockchain ecosystems like Ethereum, Polygon, and more.

# Protocol Support

The wallet-api currently supports OpenID4VCI Draft 11 and Draft 13, and OpenID4VP Draft 14 and Draft 20.

**NOTE**: The wallet-api does not support OpenID4VP 1.0 yet, which is used by the new verifier service [waltid-verifier-api2](../waltid-verifier-api2). You should only use this api alongside the original issuer and verifier services which match the same draft specifications.

# How to use it

***Docker compose***

From the root folder, you can run the wallet-api, including the necessary configuration as well as other relevant
services and apps like the wallet frontend by the following command:

```bash
cd docker-compose && docker compose up
```

***Running the wallet API as single container***

```bash
docker run \
-p 7001:7001 -it \
-v $(pwd)/wallet-api/config:/waltid-wallet-api/config \
-v $(pwd)/wallet-api/data:/waltid-wallet-api/data \
-t waltid/wallet-api
```

- Visit the web wallet hosted under [localhost:7101](http://localhost:7101).
- Visit the wallet-api hosted under [localhost:7001](http://localhost:7001).

***Build the container***

Update the wallet-api container by running the following commands from the waltid-identity root path:

```bash
# Development (local Docker daemon, single-arch)
./gradlew :waltid-services:waltid-wallet-api:jibDockerBuild
# image: waltid/wallet-api:<version>
```

```bash
# Production (multi-arch push to your registry)
export DOCKER_USERNAME=<your-dockerhub-namespace>
export DOCKER_PASSWORD=<your-dockerhub-token>
./gradlew :waltid-services:waltid-wallet-api:jib
# image: docker.io/<DOCKER_USERNAME>/wallet-api:<version>
```

Note: multi-arch images require a registry push. Local tar output is single-arch only.

# Database configuration

Currently, the following databases can be used:

- sqlite
- postgres 
- microsoft sql server 

The configuration file `db.conf` contain the datasource info required to connect to the respective database engine.
For more details about database and datasource,
refer to https://github.com/JetBrains/Exposed/wiki/DataBase-and-DataSource.

## Switching databases

Switching to use a database engine requires the following steps:

1. update `config/db.config` to point to the correct datasource configuration
2. start the database engine
3. in your IDE, run _src/main/kotlin/id/walt/webwallet/Main.kt_

### Sqlite

1. config/db.config:
```kotlin
jdbcUrl = "jdbc:sqlite:data/wallet.db"
driverClassName = "org.sqlite.JDBC"
```
2. the database file will be created by default at the `data/wallet.db` location

### Postgres

1. config/db.config:
```kotlin
jdbcUrl = "jdbc:postgresql://127.0.0.1:5432/waltid"
driverClassName = "org.postgresql.Driver"
username = "postgres"
password = "postgres"
```
2. start postgres:

```bash
docker run --name postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_USER=postgres -p 5432:5432 -d postgres
```

### Microsoft Sql Server

1. config/db.config:
```kotlin
jdbcUrl = "jdbc:sqlserver://localhost:1433;databaseName=master"
driverClassName = "com.microsoft.sqlserver.jdbc.SQLServerDriver"
```
2. start sql server:

```bash
docker run --name mssql -e "ACCEPT_EULA=Y" -e "MSSQL_SA_PASSWORD=p@ssw0rd" -p 1433:1433 --hostname mssql -d mcr.microsoft.com/mssql/server:2022-latest
```

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
