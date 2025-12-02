# IdentityCredential

```json
{
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiableCredential", "IdentityCredential"],
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "credentialSubject": {
      "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
      "given_name": "John",
      "family_name": "Doe",
      "email": "johndoe@example.com",
      "phone_number": "+1-202-555-0101",
      "address": {
        "street_address": "123 Main St",
        "locality": "Anytown",
        "region": "Anystate",
        "country": "US"
      },
      "birthdate": "1940-01-01",
      "is_over_18": true,
      "is_over_21": true,
      "is_over_65": true
    },
    "issuer": {
        "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        "name": "Government of Anytown"
    },
    "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION"
}
```

## Manifest

```json
{
    "claims": {
      "Name": "$.credentialSubject.given_name",
      "Family Name": "$.credentialSubject.family_name",
      "Email": "$.credentialSubject.email",
      "Phone number": "$.credentialSubject.phone_number",
      "Address": "$.credentialSubject.address",
      "Date of Birth": "$.credentialSubject.birthdate",
      "Over 18": "$.credentialSubject.is_over_18",
      "Over 21": "$.credentialSubject.is_over_21",
      "Over 65": "$.credentialSubject.is_over_65"
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
