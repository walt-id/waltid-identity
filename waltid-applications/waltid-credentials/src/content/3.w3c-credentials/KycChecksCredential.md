# KycChecksCredential

```json
{
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiableCredential", "VerifiableAttestation", "KycChecksCredential"],
    "credentialSubject": {
        "type": "KYC",
        "result": "SUCCESS",
        "date": "2023-07-10T14:45:10+02:00",
        "checks": [
            {
                "type": "PEP",
                "result": "SUCCESS",
                "date": "2023-07-10T14:45:10+02:00"
            },
            {
                "type": "AML",
                "result": "SUCCESS",
                "date": "2023-07-10T14:45:10+02:00"
            },
            {
                "type": "KYC",
                "result": "SUCCESS",
                "date": "2023-07-10T14:45:10+02:00"
            }
        ]
    },
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "issued": "2021-08-31T00:00:00Z",
    "issuer": {
        "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        "image": {
            "id": "https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/1660296169313-K159K9WX8J8PPJE005HV/Walt+Bot_Logo.png?format=100w",
            "type": "Image"
        },
        "name": "CH Authority",
        "type": "Profile",
        "url": "https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/1660296169313-K159K9WX8J8PPJE005HV/Walt+Bot_Logo.png?format=100w"
    },
    "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "evidence": {
        "documentPresence": "Digital",
        "evidenceDocument": "KYC",
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
        "Type": "$.credentialSubject.type",
        "Overall Result": "$.credentialSubject.result",
        "KYC Date": "$.credentialSubject.date",
        "PEP Check Result": "$.credentialSubject.checks[0].result",
        "PEP Check Date": "$.credentialSubject.checks[0].date",
        "AML Check Result": "$.credentialSubject.checks[1].result",
        "AML Check Date": "$.credentialSubject.checks[1].date",
        "KYC Check Result": "$.credentialSubject.checks[2].result",
        "KYC Check Date": "$.credentialSubject.checks[2].date"
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