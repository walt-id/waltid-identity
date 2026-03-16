# TermsAndConditions

```json
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://w3id.org/gaia-x/development#"
  ],
  "type": ["VerifiableCredential" ,"gx:TermsAndConditions"],
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "credentialSubject": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "url": "https://gaia-x.eu/",
    "hash": "4bd7554097444c960292b4726c2efa1373485e8a5565d94d41195214c5e0ceb3"
  },
  "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION"
}
```

## Manifest

```json
{
    "claims": {
        "Gaia-X Terms and Conditions": "$.credentialSubject['gx:termsAndConditions']"
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
