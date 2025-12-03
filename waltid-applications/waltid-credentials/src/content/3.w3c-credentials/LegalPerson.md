# LegalPerson

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
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
    "type": "gx:LegalPerson",
    "gx:legalName": "Example Org",
    "gx:legalRegistrationNumber": {
      "id": "https://example.org/gaiax-legal-registration-number/68a5bbea9518e7e2ac1cc75bcc8819a7edd5c4711e073ffa4bb260034dc6423c/data.json"
    },
    "gx:headquarterAddress": {
      "gx:countrySubdivisionCode": "FR-75"
    },
    "gx:legalAddress": {
      "gx:countrySubdivisionCode": "FR-75"
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