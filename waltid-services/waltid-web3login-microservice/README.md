<div align="center">
<h1>walt.id NFT Microservice</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Microservice for fetching and managing NFT data across multiple blockchain ecosystems</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
  
  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟡%20Unmaintained-yellow?style=for-the-badge&logo=warning" alt="Status: Unmaintained" />
    <br/>
    <em>This project is not actively maintained. Certain features may be outdated or not working as expected. We encourage users to contribute to the project to help keep it up to date.</em>
  </p>

</div>

## What This Service Contains

`waltid-web3login-microservice` is a standalone Ktor microservice that provides NFT (Non-Fungible Token) data fetching and management capabilities across multiple blockchain ecosystems. It serves as a backend service for NFT-related operations in wallet applications.

## Main Purpose

This microservice enables:

- **NFT Data Fetching**: Retrieve NFT tokens and details from multiple blockchain networks
- **Multi-Chain Support**: Support for Ethereum, Tezos, Flow, Near, Polkadot, and Algorand ecosystems
- **Token Filtering**: Filter and search NFTs by account and network
- **Marketplace Integration**: Retrieve marketplace data for NFTs
- **Chain Explorer Integration**: Get blockchain explorer links for contracts and tokens

## Key Concepts

### Supported Ecosystems

The service supports the following blockchain ecosystems:

- **Ethereum**: EVM-compatible chains (Ethereum, Polygon, Mumbai, etc.)
- **Tezos**: Tezos blockchain networks
- **Flow**: Flow blockchain networks
- **Near**: NEAR Protocol networks
- **Polkadot**: Polkadot parachains
- **Algorand**: Algorand networks

### NFT Operations

- **Chain Listing**: Get available networks for an ecosystem
- **Token Listing**: Fetch all NFTs owned by a wallet address on a specific chain
- **Token Details**: Get detailed information about a specific NFT
- **Token Filtering**: Filter NFTs across multiple accounts and networks
- **Marketplace Data**: Retrieve marketplace URLs for NFT trading
- **Explorer Links**: Get blockchain explorer links for contracts

## Assumptions and Dependencies

### Platform Support

- **JVM Only**: This is a JVM-only service
- **Ktor Framework**: Built with Ktor server framework
- **Port**: Runs on port 8080 by default

### Dependencies

- **Ktor Server**: HTTP server framework
- **waltid-nftkit**: NFT data fetching library for multiple blockchains
- **Kotlinx Serialization**: JSON serialization
- **Kotlinx Coroutines**: Asynchronous operations

## Usage

### Running the Service

#### Development

```bash
./gradlew run
```

The service will start on `http://localhost:8080`.

#### Production

Build and run the JAR:

```bash
./gradlew build
java -jar build/libs/waltid-web3login-microservice-0.0.1.jar
```

### API Endpoints

#### Get Chains for Ecosystem

```http
GET /nft/chains/{ecosystem}
```

Returns available networks for the specified ecosystem (e.g., `ethereum`, `tezos`, `flow`).

#### List NFTs

```http
GET /nft/list/{account}/{chain}
```

Returns all NFTs owned by the account address on the specified chain.

#### Get NFT Details

```http
GET /nft/detail/{account}/{chain}/{contract}/{tokenId}?collectionId={collectionId}
```

Returns detailed information about a specific NFT.

#### Filter NFTs

```http
GET /nft/filter?accountId={accountId}&network={network}
```

Filters NFTs across multiple accounts and networks.

#### Get Marketplace Data

```http
GET /nft/marketplace/{chain}/{contract}/{tokenId}
```

Returns marketplace information for an NFT.

#### Get Explorer Link

```http
GET /nft/explorer/{chain}/{contract}
```

Returns blockchain explorer link for a contract.

### Example Usage

```kotlin
// Get Ethereum chains
val chains = httpClient.get<List<ChainDataTransferObject>>(
    "http://localhost:8080/nft/chains/ethereum"
)

// List NFTs for an account
val nfts = httpClient.get<List<NftDetailDataTransferObject>>(
    "http://localhost:8080/nft/list/0x123.../mumbai"
)

// Get NFT details
val nft = httpClient.get<NftDetailDataTransferObject>(
    "http://localhost:8080/nft/detail/0x123.../mumbai/0x456.../1"
)
```

## Related Services

- **[waltid-wallet-api](../waltid-wallet-api)**: Complete wallet API service (may include NFT functionality)
- **[waltid-web-web3login](../../waltid-applications/waltid-web-web3login)**: Web3 login frontend application

## Configuration

The service can be configured via environment variables or configuration files. Key configuration areas:

- **Port**: Default 8080 (configurable)
- **NFT Kit Configuration**: Configured through waltid-nftkit
- **Marketplace Configuration**: Marketplace URLs and settings
- **Explorer Configuration**: Blockchain explorer URLs

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>

