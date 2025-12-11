# LegalRegistrationNumberEORI 


```json
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://w3id.org/gaia-x/development#"
  ],
  "type": [
    "VerifiableCredential",
    "gx:EORI"
  ],
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "name": "EORI Registration Number",
  "description": "Economic Operators Registration and Identification registration number",
  
  "credentialSubject": {
    "id": "https://example.org/subjects/123",
    "type": "gx:EORI",
    "gx:eori": "FR53740792600014",
    "gx:country": ""
  },
  "evidence": {
    "gx:evidenceOf": "gx:EORI",
    "gx:evidenceURL": "https://ec.europa.eu/taxation_customs/dds2/eos/validation/services/validation",
    "gx:executionDate": "2025-09-17T09:16:14.928+00:00"
  }
}
```

## Manifest

```json
{
    "claims": {
        "Legal Registration Number": "$.credentialSubject.id",
        "EORI": "$.credentialSubject['gx:eori']",
        "Country": "$.credentialSubject['gx:country']"

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