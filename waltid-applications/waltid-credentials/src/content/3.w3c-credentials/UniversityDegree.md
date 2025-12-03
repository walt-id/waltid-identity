# UniversityDegree

```json
{
    "@context": ["https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"],
    "id": "http://example.gov/credentials/3732",
    "type": ["VerifiableCredential", "UniversityDegree"],
    "issuer": {
        "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION"
    },
    "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "credentialSubject": {
        "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        "degree": {
            "type": "BachelorDegree",
            "name": "Bachelor of Science and Arts"
        }
    }
}
```

## Manifest

```json
{
    "claims": {
        "Credential Subject ID": "$.credentialSubject.id",
        "Degree Type": "$.credentialSubject.degree.type",
        "Degree Name": "$.credentialSubject.degree.name"
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