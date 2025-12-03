# MortgageEligibility

```json
{
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "type": [
      "VerifiableCredential", 
      "VerifiableAttestation", 
      "VerifableId", 
      "MortgageEligibility"
    ],
    "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "issued": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "credentialSubject": {
        "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        "salutation": "",
        "familyName": "",
        "firstName": "",
        "emailAddress": "",
        "dateOfBirth": "",
        "purchasePrice": "",
        "totalIncome": "",
        "mortgageAmount": "",
        "additionalCollateral": "",
        "postCodeProperty": ""
    }
}
```

## Manifest

```json
{
    "claims": {
        "Family Name": "$.credentialSubject.familyName",
        "First Name": "$.credentialSubject.firstName",
        "Email Address": "$.credentialSubject.emailAddress",
        "Date of Birth": "$.credentialSubject.dateOfBirth",
        "Purchase Price": "$.credentialSubject.purchasePrice",
        "Total Income": "$.credentialSubject.totalIncome",
        "Mortgage Amount": "$.credentialSubject.mortgageAmount",
        "Additional Collateral": "$.credentialSubject.additionalCollateral",
        "Post Code Property": "$.credentialSubject.postCodeProperty"
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
    "issued": "<timestamp>",
    "expirationDate": "<timestamp-in:365d>"
}
```