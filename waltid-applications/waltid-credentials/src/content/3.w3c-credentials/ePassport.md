# ePassport

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1"
  ],
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "type": [
    "VerifiableCredential",
    "ePassport"
  ],
  "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "credentialSubject": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "fullName": "John Doe",
    "givenName": "John",
    "familyName": "Doe",
    "nationality": "AUT",
    "residency": {
      "country": "Austria",
      "countryCode": "AT",
      "city": "Wien"
    },
    "passportNumber": "P12345678",
    "issuingCountry": "AUT",
    "dateOfBirth": "1990-04-15",
    "authority": "Wien",
    "sex": "M",
    "placeOfBirth": {
      "country": "Austria",
      "city": "Graz"
    },
    "type": "P",
    "code": "AUT",
    "height": "182 cm",
    "eyeColor": "Brown",
    "issuanceDate": "2021-06-15",
    "expirationDate": "2031-06-15"
  }
}
```

## Manifest

```json
{
  "claims": {
    "Full name": "$.credentialSubject.fullName",
    "Given name": "$.credentialSubject.givenName",
    "Family name": "$.credentialSubject.familyName",
    "Nationality": "$.credentialSubject.nationality",
    "Residence country": "$.credentialSubject.residency.country",
    "Residence country code": "$.credentialSubject.residency.countryCode",
    "Residence city": "$.credentialSubject.residency.city",
    "Passport number": "$.credentialSubject.passportNumber",
    "Issuing country": "$.credentialSubject.issuingCountry",
    "Date of birth": "$.credentialSubject.dateOfBirth",
    "Issuing authority": "$.credentialSubject.authority",
    "Sex": "$.credentialSubject.sex",
    "Place of birth country": "$.credentialSubject.placeOfBirth.country",
    "Place of birth city": "$.credentialSubject.placeOfBirth.city",
    "Document type": "$.credentialSubject.type",
    "Document code": "$.credentialSubject.code",
    "Height": "$.credentialSubject.height",
    "Eye color": "$.credentialSubject.eyeColor",
    "Document issuance date": "$.credentialSubject.issuanceDate",
    "Document expiration date": "$.credentialSubject.expirationDate"
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