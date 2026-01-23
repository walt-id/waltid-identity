# GaiaXTermsAndConditions

```json
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://w3id.org/gaia-x/development#"
  ],
  "type": ["VerifiableCredential" ,"gx:Issuer"],
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "credentialSubject": {
    "id": "https://delta-dao.com/.well-known/2503_gx_TandC_deltaDAO.json",
    "gaiaxTermsAndConditions": "4bd7554097444c960292b4726c2efa1373485e8a5565d94d41195214c5e0ceb3"
  },
  "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION"
}
```

## Manifest

```json
{
    "claims": {
        "Gaia-X Terms and Conditions": "$.credentialSubject['gaiaxTermsAndConditions']"
    }
}
```

## Mapping example

```json
{
  "id": "<uuid>",
  "issuer": "<issuerDid>",
  "issuanceDate": "<timestamp>",
  "expirationDate": "<timestamp-in:365d>"
}
```