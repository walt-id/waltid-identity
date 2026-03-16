# RegistrationNumber

```json
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://w3id.org/gaia-x/development#"
  ],
  "type": [
    "VerifiableCredential",
    "gx:RegistrationNumber"
  ],
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "credentialSubject": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "leiCode": "9695007586GCAKPYJ703",
    "countryCode": "FR"
  },
  "evidence": [
    {
      "gx:evidenceURL": "https://api.gleif.org/api/v1/lei-records/",
      "gx:executionDate": "2025-02-23T16:42:16.058Z",
      "gx:evidenceOf": "gx:leiCode"
    }
  ]
}
```

## Manifest

```json
{
    "claims": {
        "Legal Registration Number": "$.credentialSubject.id",
        "LEI Code": "$.credentialSubject['gx:leiCode']",
        "LEI Country Code": "$.credentialSubject['gx:leiCode-countryCode']"
    }
}
```

## Mapping example

```json
{
  "id": "<uuid>",
  "issuer": "<issuerDid>",
  "credentialSubject": {
    "id": "<subjectDid>"
  },
  "issuanceDate": "<timestamp>",
  "expirationDate": "<timestamp-in:365d>"
}
```
