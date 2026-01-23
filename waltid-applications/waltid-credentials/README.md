
<div align="center">
 <h1>Credential Repository</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>A comprehensive repository of verifiable credential schemas and examples, providing documentation and templates for W3C Verifiable Credentials, SD-JWT credentials, and ISO mDoc credentials.</p>

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

**Hosted at:** https://credentials.walt.id/

## What It Does

This Nuxt-based documentation site serves as a reference repository for credential schemas across multiple standards:

- **W3C Verifiable Credentials** - JSON-LD credentials with manifests and mapping examples
- **SD-JWT Credentials** - IETF selective disclosure JWT credentials
- **ISO mDoc Credentials** - ISO 18013-5 mobile document credentials

Each credential includes example structures, available claims, and usage guidance.

## Setup

```shell
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build
```

## Docker

```shell
docker build -t waltid/vc-repository .
docker run -p3000:3000 -i -t waltid/vc-repository
```

## Adding a New Credential

### 1. Choose the Credential Type

Navigate to the appropriate directory:
- `src/content/1.iso-mdoc-credentials/` - ISO mDoc credentials
- `src/content/2.sd-jwt-credentials/` - SD-JWT credentials  
- `src/content/3.w3c-credentials/` - W3C Verifiable Credentials

### 2. Create the Markdown File

Create a new `.md` file **named after your credential** (e.g., `MyCredential.md`):

```markdown
# Credential Name

Brief description of the credential.

## Example Structure

\```json
{
  "@context": ["https://www.w3.org/2018/credentials/v1"],
  "type": ["VerifiableCredential", "MyCredential"],
  "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "credentialSubject": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "field1": "value1",
    "field2": "value2"
  }
}
\```

## Manifest

\```json
{
  "claims": {
    "Field 1": "$.credentialSubject.field1",
    "Field 2": "$.credentialSubject.field2"
  }
}
\```

## Mapping example

\```json
{
  "id": "<uuid>",
  "issuer": "<issuerDid>",
  "credentialSubject": {
    "id": "<subjectDid>"
  },
  "issuanceDate": "<timestamp>"
}
\```
```

### 3. Preview Changes

Run the development server to see your new credential in the navigation:

```shell
npm run dev
```

Your credential will automatically appear in the left sidebar organized by credential type.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
