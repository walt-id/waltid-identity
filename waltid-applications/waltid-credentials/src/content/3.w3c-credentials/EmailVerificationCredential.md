# EmailVerificationCredential

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
    "https://w3id.org/email/v1"
  ],
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "type": [
    "VerifiableCredential",
    "EmailVerificationCredential"
  ],
  "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "credentialSubject": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "name": "Christine Schmidt",
    "email": "user@example.com",
    "emailVerified": true,
    "verifiedAt": "2025-05-12"
  }
}
```

## Manifest

```json
{
    "claims": {
        "Name": "$.credentialSubject.name",
        "Email": "$.credentialSubject.email",
        "Email Verified": "$.credentialSubject.emailVerified",
        "Verified At": "$.credentialSubject.verifiedAt"
    }
}
```

## Mapping example

```json
{
   "id":"<uuid>",
   "issuer":"<issuerDid>",
   "credentialSubject":{
      "id":"<subjectDid>"
   },
   "issuanceDate":"<timestamp>"
}
```