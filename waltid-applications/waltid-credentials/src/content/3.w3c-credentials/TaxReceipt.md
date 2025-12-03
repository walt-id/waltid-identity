# TaxReceipt

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1"
  ],
  "type": [
    "VerifiableCredential",
    "TaxReceipt"
  ],
  "credentialSchema": {
    "id": "https://insert-link",
    "type": "FullJsonSchemaValidator2021"
  },
  "credentialSubject": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "ReceiptId": "string",
    "VAT": "string"
  },
  "evidence": [
    {
      "documentPresence": [
        "Physical"
      ],
      "evidenceDocument": [
        "Passport"
      ],
      "subjectPresence": "Physical",
      "type": [
        "DocumentVerification"
      ],
      "verifier": "did:ebsi:2A9BZ9SUe6BatacSpvs1V5CdjHvLpQ7bEsi2Jb6LdHKnQxaN"
    }
  ],
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issued": "2021-08-31T00:00:00Z",
  "issuer": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "name": "Tax Authority"
  },
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION"
}
```

## Manifest

```json
{
    "claims": {
        "Receipt ID": "$.credentialSubject.ReceiptId",
        "VAT": "$.credentialSubject.VAT"
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