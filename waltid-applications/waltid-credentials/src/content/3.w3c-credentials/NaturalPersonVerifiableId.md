# NaturalPersonVerifiableID

```json
{
  "@context": ["https://www.w3.org/2018/credentials/v1" ],
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "type": [ "VerifiableCredential", "VerifiableAttestation", "NaturalPersonVerifiableID"],
  "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issued": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "credentialSubject": {
      "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
      "familyName": "Doe", 
      "firstName": "Jane",
      "dateOfBirth":  "1985-08-15", 
      "personalIdentifier": "c02654b4-814e-46dd-ba17-0bd81a45057c",
      "nameAndFamilyNameAtBirth": "Jane Doe",
      "placeOfBirth": "Lille, France",
      "currentAddress": "1 Boulevard de la Liberté, 59800 Lille",
      "gender": "Female"
  },
  "credentialSchema": {
      "id": "https://api-conformance.ebsi.eu/trusted-schemas-registry/v3/schemas/z22ZAMdQtNLwi51T2vdZXGGZaYyjrsuP1yzWyXZirCAHv",
      "type": "FullJsonSchemaValidator2021"
  }
}
```

## Manifest

```json
{
    "claims": {
        "Name": "$.credentialSubject.firstName",
        "Last Name": "$.credentialSubject.familyName",
        "Date of Birth": "$.credentialSubject.dateOfBirth",
        "Gender": "$.credentialSubject.gender",
        "Personal Identifier": "$.credentialSubject.personalIdentifier",
        "Name and Family Name at Birth": "$.credentialSubject.nameAndFamilyNameAtBirth",
        "Place of Birth": "$.credentialSubject.placeOfBirth",
        "Current Address": "$.credentialSubject.currentAddress"
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
    "issuanceDate": "<timestamp-ebsi>",
    "issued": "<timestamp-ebsi>",
    "expirationDate": "<timestamp-ebsi-in:365d>"
}
```