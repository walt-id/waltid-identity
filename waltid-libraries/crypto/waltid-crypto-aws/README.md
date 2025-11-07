<div align="center">
 <h1>AWS SDK Extension for walt.id Crypto</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
  <p>A Kotlin-based extension that enhances walt.id crypto with native AWS key management capabilities.</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://twitter.com/intent/follow?screen_name=walt_id">
<img src="https://img.shields.io/twitter/follow/walt_id.svg?label=Follow%20@walt_id" alt="Follow @walt_id" />
</a>
</div>

## Overview

This extension introduces `AwsKey`, a robust implementation leveraging the AWS SDK for Kotlin to manage cryptographic keys. It serves as a more integrated alternative to the platform-agnostic `AWSKeyRestAPI` found in the base walt.id crypto library.

## Key Features

- Native AWS SDK integration for optimal performance
- Kotlin-specific implementation
- Seamless key management through AWS KMS
- Direct SDK access instead of REST API calls

## Authentication

The extension utilizes AWS SDK's default credential provider chain for authentication, automatically detecting credentials from multiple sources including:

- Environment variables
- AWS credentials file
- IAM roles for EC2
- Container credentials
- SSO credentials

## Comparison to Base Implementation

While the base `AWSKeyRestAPI` offers cross-platform compatibility through REST endpoints, this extension provides:

- Improved performance through direct SDK calls
- Enhanced error handling
- Native integration with AWS services

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [Twitter](https://mobile.twitter.com/walt_id)
* Get help, request features and report bugs: [GitHub Issues ](https://github.com/walt-id/waltid-identity/issues)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

