# LegalPerson

```json
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://w3id.org/gaia-x/development#"
  ],
  "type": ["VerifiableCredential", "gx:LegalPerson"],
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "name": "Legal Person",
  "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "credentialSubject": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "name": "Consumer Organization",
    "registrationNumber": {
      "id": "https://gaia-x.eu/legalRegistrationNumber.json"
    },
    "headquartersAddress": {
      "countryCode": "FR"
    },
    "legalAddress": {
      "countryCode": "FR"
    }
  }
}
```

## Manifest

```json
{
    "claims": {
        "Legal Name": "$.credentialSubject['gx:legalName']",
        "Legal Registration Number": "$.credentialSubject['gx:legalRegistrationNumber'].id",
        "Headquarter Address Country Subdivision": "$.credentialSubject['gx:headquarterAddress']['gx:countrySubdivisionCode']",
        "Legal Address Country Subdivision": "$.credentialSubject['gx:legalAddress']['gx:countrySubdivisionCode']"
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
