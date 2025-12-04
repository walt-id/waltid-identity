# KycDataCredential

```json
{
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiableCredential", "VerifiableAttestation", "KycDataCredential"],
    "credentialSubject": {
        "identificationprocess": {
            "agentname": "TROBOT",
            "companyId": "1234",
            "result": "SUCCESS",
            "identificationTime": "2023-07-10T14:45:10+02:00",
            "identificationId": "TST-ELXNG",
            "transactionNumber": "1234",
            "type": "APP"
        },
        "userData": {
            "dateOfBirth": "1993-04-08",
            "familyName": "DOE",
            "firstName": "Jane"
        },
        "identificationdocument": {
            "type": "IDCARD",
            "country": "DE",
            "validuntil": "2034-08-01",
            "number": "L01X00T27",
            "dateissued": "2021-08-31"
        },
        "performedKycChecks": {
            "livenessscreenshot": true,
            "securityfeaturevideo": true
        }
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
        "First Name": "$.credentialSubject.userData.firstName",
        "Family Name": "$.credentialSubject.userData.familyName",
        "Date of Birth": "$.credentialSubject.userData.dateOfBirth",
        "Document Type": "$.credentialSubject.identificationdocument.type",
        "Document Country": "$.credentialSubject.identificationdocument.country",
        "Document Valid Until": "$.credentialSubject.identificationdocument.validuntil",
        "Document Number": "$.credentialSubject.identificationdocument.number",
        "Document Date Issued": "$.credentialSubject.identificationdocument.dateissued",
        "Identification Agent Name": "$.credentialSubject.identificationprocess.agentname",
        "Identification Company ID": "$.credentialSubject.identificationprocess.companyId",
        "Identification Result": "$.credentialSubject.identificationprocess.result",
        "Identification Time": "$.credentialSubject.identificationprocess.identificationTime",
        "Identification ID": "$.credentialSubject.identificationprocess.identificationId",
        "Identification Transaction Number": "$.credentialSubject.identificationprocess.transactionNumber",
        "Identification Type": "$.credentialSubject.identificationprocess.type"
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