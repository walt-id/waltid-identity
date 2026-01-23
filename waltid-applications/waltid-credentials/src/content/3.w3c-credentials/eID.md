# eID

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1"
  ],
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "type": [
    "VerifiableCredential",
    "eID"
  ],
  "issuer": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "name": "Department of Home Affairs, Cape Town"
  },
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "credentialSubject": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "fullName": "John Doe",
    "firstName": "John",
    "lastName": "Doe",
    "nationality": "ZAF",
    "documentId": "123456789",
    "issuingCountry": "ZAF",
    "dateOfBirth": "1990-04-15",
    "sex": "M",
    "placeOfBirth": {
      "country": "South Africa",
      "city": "Johannesburg"
    },
    "address": "123 Kloof Street, Cape Town, 8001, South Africa"
  }
}
```

## Manifest

```json
{
  "claims": {
    "Document ID": "$.credentialSubject.documentId",
    "First Name": "$.credentialSubject.firstName",
    "Last Name": "$.credentialSubject.lastName",
    "Date of Birth": "$.credentialSubject.dateOfBirth",
    "Sex": "$.credentialSubject.sex",
    "Nationality": "$.credentialSubject.nationality",
    "Issuing Country": "$.credentialSubject.issuingCountry",
    "Place of Birth Country": "$.credentialSubject.placeOfBirth.country",
    "Place of Birth City": "$.credentialSubject.placeOfBirth.city",
    "Address": "$.credentialSubject.address"
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
  "issuanceDate": "<timestamp>",
  "expirationDate": "<timestamp-in:365d>"
}
```