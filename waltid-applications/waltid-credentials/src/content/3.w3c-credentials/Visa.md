# Visa

```json
{
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiableCredential", "VerifiableAttestation", "Visa"],
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "credentialSubject": {
        "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        "firstName": "John",
        "lastName": "Doe",
        "passportNumber": "G7F2A04F7O",
        "dateOfBirth": "1980-01-01",
        "gender": "Male",
        "nationality": "US",
        "visaType": "Tourist",
        "entryNumber": "Single",
        "visaValidity": {
            "start": "2024-01-01",
            "end": "2024-06-30"
        },
        "purposeOfVisit": "Tourism"
    },
    "issuer": {
        "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        "name": "Embassy of Wonderland"
    },
    "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION"
}
```

## Manifest

```json
{
    "claims": {
        "Passport Number": "$.credentialSubject.passportNumber",
        "Name": "$.credentialSubject.firstName",
        "Last Name": "$.credentialSubject.lastName",
        "Date of Birth": "$.credentialSubject.dateOfBirth",
        "Gender": "$.credentialSubject.gender",
        "Nationality": "$.credentialSubject.nationality",
        "Visa Type": "$.credentialSubject.visaType",
        "Entry Number": "$.credentialSubject.entryNumber",
        "Visa Issuance Date": "$.credentialSubject.visaValidity.start",
        "Visa Expiration Date": "$.credentialSubject.visaValidity.end",
        "Purpose of Visit": "$.credentialSubject.purposeOfVisit"
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