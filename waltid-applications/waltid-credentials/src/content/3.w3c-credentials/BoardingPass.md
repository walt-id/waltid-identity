# BoardingPass

```json
{
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiableCredential", "VerifiableAttestation", "BoardingPass"],
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "credentialSubject": {
        "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        "firstName": "John",
        "lastName": "Doe",
        "seat": "1A",
        "flight": "LH123",
        "date": "09/01/2021"
    },
    "issuer": {
        "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        "name": "Airline"
    },
    "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION"
}
```

## Manifest

```json
{
    "claims": {
        "Date": "$.credentialSubject.date",
        "Name": "$.credentialSubject.firstName",
        "Last Name": "$.credentialSubject.lastName",
        "Flight": "$.credentialSubject.flight",
        "Seat": "$.credentialSubject.seat"
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