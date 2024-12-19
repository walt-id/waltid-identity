# AWS SDK Extension for walt.id Crypto

A Kotlin-based extension that enhances walt.id crypto with native AWS key management capabilities.

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

