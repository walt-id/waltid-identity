# walt.id Credentials

A comprehensive repository of verifiable credential schemas and examples, providing documentation and templates for W3C Verifiable Credentials, SD-JWT credentials, and ISO mDoc credentials.

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
