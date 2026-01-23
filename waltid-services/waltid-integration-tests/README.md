<div align="center">
<h1>walt.id Integration Tests</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Integration test suite for walt.id Community Stack services</p>

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

## What This Module Contains

`waltid-integration-tests` is a comprehensive integration test suite for the walt.id Community Stack services. It provides automated testing for credential issuance, verification, wallet operations, and end-to-end flows across the walt.id ecosystem.

## Main Purpose

This test suite enables:

- **Service Integration Testing**: Test interactions between Issuer API, Verifier API, and Wallet API
- **Credential Format Testing**: Validate W3C, SD-JWT, and mdoc credential operations
- **Protocol Testing**: Test OpenID4VCI and OpenID4VP protocol implementations
- **End-to-End Flows**: Validate complete credential issuance and verification workflows
- **API Validation**: Ensure service APIs function correctly in integrated scenarios

## Key Concepts

### Test Architecture

- **In-Memory Environment**: Tests run against an in-memory Community Stack environment
- **Test Execution Listener**: Automatically starts Ktor applications before test runs
- **Abstract Base Class**: All tests inherit from `AbstractIntegrationTest` for common setup/teardown
- **Independent Tests**: Tests are designed to run independently without execution order dependencies

### Test Coverage

The suite includes tests for:

- **Credential Issuance**: SD-JWT, JWT, and mdoc credential issuance flows
- **Credential Verification**: Presentation and verification flows
- **Wallet Operations**: DID management, key management, credential storage
- **Batch Operations**: Batch credential issuance
- **User Authentication**: Email account and X5C user authentication
- **Category Management**: Credential categorization in wallets

## Assumptions and Dependencies

### Platform Support

- **JVM Only**: This is a JVM-only test suite
- **Java 21+**: Requires Java 21 or later

### Dependencies

- **JUnit 5**: Test framework
- **Ktor Test Host**: For testing Ktor applications
- **waltid-service-commons-test**: Test utilities
- **waltid-issuer-api**: Issuer API service
- **waltid-verifier-api**: Verifier API service
- **waltid-wallet-api**: Wallet API service
- **Kotlinx Coroutines**: For async test execution

## Usage

### Running Tests

#### With Gradle

From the project root:

```bash
./gradlew :waltid-services:waltid-integration-tests:test
```

#### With IntelliJ IDEA

1. **Run all tests**: Right-click on `src/main/kotlin` and select "Run Tests"
2. **Run single test**: Right-click on a test class and select "Run ..."

**Note**: If configuration cannot be loaded, update the run configuration and set the working directory to the module root directory.

### Writing Tests

All tests should:

1. **Location**: Be placed in `src/main/kotlin` under the `id.walt.test.integration.tests` package (or sub-packages)
2. **Inheritance**: Extend `AbstractIntegrationTest` which handles setup/teardown (deletes all credentials and categories)
3. **Independence**: Be written to execute independently (no execution order dependencies)
4. **Naming**: Follow JUnit 5 test conventions

### Example Test Structure

```kotlin
import id.walt.test.integration.tests.AbstractIntegrationTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MyIntegrationTest : AbstractIntegrationTest() {
    
    @Test
    fun testFeature() = runTest {
        // Test implementation
        val walletApi = defaultWalletApi
        val issuerApi = environment.getIssuerApi()
        // ... test logic
    }
}
```

## Test Organization

Tests are organized by functionality:

- **Credential Issuance**: `IssueSdJwtCredentialIntegrationTest`, `IssueJwtCredentialIntegrationTest`, `IssueIetfSdJwtDidCredentialIntegrationTest`
- **Wallet Operations**: `DidsWalletIntegrationTest`, `KeysWalletIntegrationTest`, `CategoryWalletIntegrationTest`
- **Verification**: `VerifierPresentedCredentialsIntegrationTests`
- **MDoc**: `MdocIntegrationTest`
- **User Management**: `EmailAccountUserWalletIntegrationTest`, `X5cUserWalletIntegrationTest`

## Related Modules

- **[waltid-service-commons-test](../waltid-service-commons-test)**: Test utilities and helpers
- **[waltid-e2e-tests](../waltid-e2e-tests)**: End-to-end test suite (replaced by this module)
- **[waltid-issuer-api](../waltid-issuer-api)**: Issuer API service being tested
- **[waltid-verifier-api](../waltid-verifier-api)**: Verifier API service being tested
- **[waltid-wallet-api](../waltid-wallet-api)**: Wallet API service being tested

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
