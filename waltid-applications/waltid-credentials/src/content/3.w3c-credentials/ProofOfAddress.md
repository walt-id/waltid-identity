# ProofOfAddress

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1"
  ],
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "type": [
    "VerifiableCredential",
    "ProofOfAddress"
  ],
  "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "credentialSubject": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "firstName": "John",
    "lastName": "Doe",
    "address": {
      "country": "Austria",
      "countryCode": "AT",
      "streetAddress": "Landstraßer Hauptstraße 12",
      "apartmentOrSuite": "Top 7",
      "postalCode": "1030",
      "city": "Wien"
    }
  }
}
```

## Manifest

```json
{
  "claims": {
    "First name": "$.credentialSubject.firstName",
    "Last name": "$.credentialSubject.lastName",
    "Address country": "$.credentialSubject.address.country",
    "Address country code": "$.credentialSubject.address.countryCode",
    "Street address": "$.credentialSubject.address.streetAddress",
    "Apartment or suite": "$.credentialSubject.address.apartmentOrSuite",
    "Postal code": "$.credentialSubject.address.postalCode",
    "City": "$.credentialSubject.address.city"
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