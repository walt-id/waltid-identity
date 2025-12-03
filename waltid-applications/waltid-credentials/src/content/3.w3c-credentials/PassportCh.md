# PassportCh

```json
{
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "type": [
      "VerifiableCredential", 
      "VerifiableAttestation", 
      "VerifableId", 
      "PassportCh"
    ],
    "issuer": {
        "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        "image": {
            "id": "https://img.freepik.com/premium-photo/swiss-flag-switzerland_469558-1774.jpg",
            "type": "Image"
        },
        "name": "CH Authority",
        "type": "Profile",
        "url": "https://img.freepik.com/premium-photo/swiss-flag-switzerland_469558-1774.jpg"
    },
    "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "credentialSubject": {
        "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        "residence": "Schweiz",
        "canton": "Zürich",
        "address": "Seestraße 1",
        "passportNumber": "C7648556",
        "salutation": "",
        "familyName": "DOE",
        "givenName": "Hans",
        "birthName": "Doe",
        "dateOfBirth": "1930-10-01",
        "emailAddress": "",
        "placeOfBirth": "Zug",
        "height": "179",
        "gender": "M",
        "authority": "Aarau AG",
        "nationality": "Schweiz",
        "countryCode": "CHE",
        "passportPhoto": "data:image/png;base64,iVBORw0KGgo...kJggg=="
    },
    "evidence": {
        "documentPresence": "Physical",
        "evidenceDocument": "Passport",
        "subjectPresence": "Physical",
        "type": "DocumentVerification",
        "verifier": "did:ebsi:2A9BZ9SUe6BatacSpvs1V5CdjHvLpQ7bEsi2Jb6LdHKnQxaN"
    }
}
```

## Manifest

```json
{
    "claims": {
        "Family Name": "$.credentialSubject.familyName",
        "Given Name": "$.credentialSubject.givenName",
        "Birth Name": "$.credentialSubject.birthName",
        "Date of Birth": "$.credentialSubject.dateOfBirth",
        "Place of Birth": "$.credentialSubject.placeOfBirth",
        "Residence": "$.credentialSubject.residence",
        "Canton": "$.credentialSubject.canton",
        "Address": "$.credentialSubject.address",
        "Passport Number": "$.credentialSubject.passportNumber",
        "Height": "$.credentialSubject.height",
        "Gender": "$.credentialSubject.gender",
        "Authority": "$.credentialSubject.authority",
        "Nationality": "$.credentialSubject.nationality",
        "Country Code": "$.credentialSubject.countryCode"
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
