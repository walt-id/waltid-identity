<div align="center">
 <h1>Wallet API</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Identity wallets to manage Keys, DIDs, Credentials, and NFTs/SBTs<p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://twitter.com/intent/follow?screen_name=walt_id">
<img src="https://img.shields.io/twitter/follow/walt_id.svg?label=Follow%20@walt_id" alt="Follow @walt_id" />
</a>


</div>

## Getting Started

- [Intro Video](https://www.youtube.com/watch?v=ILaSAxjoHbw&t=1s) - Learn about features and see a demo.
- [Documentation](https://docs.oss.walt.id/wallet/api/getting-started) - Learn how to create and manage identity wallets.


### Running The Project

From the root folder, you can run the wallet-api, including the necessary configuration as well as other relevant services and apps like the wallet frontend by the following command:

```bash
cd docker-compose && docker compose up
```

- Visit the web wallet hosted under [localhost:3000](http://localhost:3000).
- Visit the wallet-api hosted under [localhost:4545](http://localhost:4545).

Update the wallet-api container by running the following commands from the root folder: 
```bash
docker build -t waltid/wallet-api -f waltid-wallet-api/Dockerfile .
```

Note the wallet-api is used by the waltid-web-wallet to provide all the functionality.

## What is the Wallet API?

The Wallet-API is designed to provide a broad range of API endpoints that let you offer identity wallets to users capable of handling different keys, DIDs, and credential types and facilitate the receipt and presentation of credentials from various issuers and verifiers using the OIDC4VC protocol standard. Alongside digital identity capabilities, it also supports the integration of web3 wallets. This feature enables your users to view their tokens from different blockchain ecosystems like Ethereum, Polygon, and more.


## Join the community

* Connect and get the latest updates: <a href="https://discord.gg/AW8AgqJthZ">Discord</a> | <a href="https://walt.id/newsletter">Newsletter</a> | <a href="https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA">YouTube</a> | <a href="https://mobile.twitter.com/walt_id" target="_blank">Twitter</a>
* Get help, request features and report bugs: <a href="https://github.com/walt-id/.github/discussions" target="_blank">GitHub Discussions</a>

## License

**Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-ssikit/blob/master/LICENSE).**
