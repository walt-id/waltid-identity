# LegalRegistrationNumberVatId

```json
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://w3id.org/gaia-x/development#"  
  ],
  "type": [
    "VerifiableCredential",
    "gx:VatID"
  ],
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "credentialSubject": {
    "id": "https://example.org/subjects/123",
    "type": "gx:VatID",
    "gx:vatID": "BE0762747721",
    "gx:countryCode": "BE"
  },
  "evidence": {
    "gx:evidenceOf": "gx:VatID",
    "gx:evidenceURL": "http://ec.europa.eu/taxation_customs/vies/services/checkVatService",
    "gx:executionDate": "2025-09-17T09:11:13.643+00:00"
  }
}
```

## Manifest

```json
{
    "claims": {
        "Legal Registration Number": "$.credentialSubject.id",
        "VAT ID": "$.credentialSubject['gx:vatID']",
        "VAT Country Code": "$.credentialSubject['gx:countryCode']"
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