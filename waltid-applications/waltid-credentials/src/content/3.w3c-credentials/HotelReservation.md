# HotelReservation

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1"
  ],
  "type": [
    "VerifiableCredential",
    "HotelReservation"
  ],
  "credentialSchema": {
    "id": "https://insert-link",
    "type": "FullJsonSchemaValidator2021"
  },
  "credentialSubject": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "familyName": "DOE",
    "firstName": "Jane",
    "currentAddress": [
      "42 Great Place, Canada"
    ],
    "dateOfBirth": "1993-04-08",
    "placeOfBirth": "LILLE, FRANCE"
  },
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issued": "2021-08-31T00:00:00Z",
  "issuer": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "name": "Hotel"
  },
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION"
}
```

## Manifest

```json
{
    "claims": {
        "First Name": "$.credentialSubject.firstName",
        "Family Name": "$.credentialSubject.familyName",
        "Current Address": "$.credentialSubject.currentAddress[0]",
        "Date of Birth": "$.credentialSubject.dateOfBirth",
        "Place of Birth": "$.credentialSubject.placeOfBirth"
    }
}
```

## Mapping example

```json
{
    "id": "<uuid>",
    "issuer": {
        "id": "<issuerDid>"
    },
    "credentialSubject": {
        "id": "<subjectDid>"
    },
    "issuanceDate": "<timestamp>"
}
```
