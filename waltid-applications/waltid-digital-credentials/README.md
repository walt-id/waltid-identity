<div align="center">
 <h1>Digital Credentials Test App</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Standalone test web app for validating the DC API flow directly against verifier endpoints</p>

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

## What This Application Contains

`waltid-digital-credentials` is a standalone test web application designed for validating the Digital Credentials API flow directly against verifier endpoints. It provides a user-friendly interface for testing and debugging DC API verification flows.

## Main Purpose

This application enables developers to:

- **Load OpenAPI Documentation**: Fetch and parse OpenAPI docs from the selected verifier (`/api.json`)
- **Browse Examples**: List `POST /verification-session/create` examples containing `dc_api`
- **Edit Payloads**: Modify JSON payloads before execution
- **Execute Verification Flow**: Run the complete DC API verification flow
- **Monitor Results**: View all request/response objects and final payloads

## Try It Out

**Live Demo**: Use our deployed demo at [digital-credentials.walt.id](https://digital-credentials.walt.id)

**Learn More** about the Digital Credentials API:
- [W3C Standard](https://wicg.github.io/digital-credentials/)
- [Ecosystem Support](https://github.com/nicofrand/browser-eid-cred-api-support)

**Tested Wallets**:
- [CM Wallet](https://credentialsmanagement.eu/)

## Key Concepts

### Verification Flow

The application executes the following flow:

1. `POST /verification-session/create` - Create a new verification session
2. `GET /verification-session/{sessionId}/request` - Retrieve the verification request
3. `navigator.credentials.get(...)` - Invoke the browser's Digital Credentials API
4. `POST /verification-session/{sessionId}/response` - Submit the credential response
5. Polls `GET /verification-session/{sessionId}/info` every 10 seconds

### Polling Rules

- **Continue**: While `status === "IN_USE"`
- **Success**: When `status === "SUCCESSFUL"`
- **Failure**: Any other status is treated as failure

### UI Controls

- **Verifier Preset**: Choose between:
  - Open Source (`https://verifier2.portal.test.waltid.cloud`)
  - Enterprise (proxied path)
- **Bearer Token**: Optional authentication token for secured verifier deployments
- **Reload Swagger Examples**: Refresh available API examples

### Logging

- All request/response objects are logged to the browser console
- Process and final payloads are displayed in the `Result Log` field

## Usage

### Prerequisites

- Node.js 18+ and npm
- Access to a verifier endpoint (Open Source or Enterprise)

### Development

From repository root:

```bash
npm run dev
```

### Docker

Build from repository root:

```bash
docker build --no-cache -t waltid/digital-credentials .
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
