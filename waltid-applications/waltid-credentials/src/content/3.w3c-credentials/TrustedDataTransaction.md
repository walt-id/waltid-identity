# TrustedDataTransaction

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
    "https://w3id.org/gaia-x/development#"
  ],
  "type": ["VerifiableCredential", "TrustedDataTransaction"],
  "id": "https://example.org/trusted-data-transaction/68a5bbea9518e7e2ac1cc75bcc8819a7edd5c4711e073ffa4bb260034dc6423c/data.json",
  "name": "Trusted Data Transaction",
  "issuer": "did:web:example.org",
  "issuanceDate": "2023-07-20T07:05:44Z",
  "expirationDate": "2033-07-20T07:05:44Z",
  "credentialSubject": {
    "id": "https://example.org/trusted-data-transaction/68a5bbea9518e7e2ac1cc75bcc8819a7edd5c4711e073ffa4bb260034dc6423c/data.json",
    "type": "gx:TrustedDataTransaction",
    "providerId": "did:web:provider",
    "consumerId": "did:web:consumer",
    "assets": ["asset-id"],
    "transactionId": "transaction-id",
    "license": {
      "@context": "http://www.w3.org/ns/odrl.jsonld",
      "@type": "Set",
      "uid": "34e5d45f-47e4-45ad-ac72-98ed699e3d80",
      "profile": "http://www.w3.org/ns/odrl/2/core",
      "permission": [
        {
          "uid": "permission-date",
          "action": "use",
          "constraint": [
            {
              "@type": "AtomicConstraint",
              "uid": "date-constraint",
              "leftOperand": "odrl:dateTime",
              "operator": "lt",
              "rightOperand": "2099-01-31"
            }
          ]
        }
      ]
    }
  }
}
```

## Manifest

```json
{
    "claims": {
        "Provider id": "$.credentialSubject['gx:TrustedDataTransaction'].providerId",
        "Consumer id": "$.credentialSubject['gx:TrustedDataTransaction'].consumerId",
        "Asset id": "$.credentialSubject['gx:TrustedDataTransaction'].assetId",
        "Transaction id": "$.credentialSubject['gx:TrustedDataTransaction'].transactionId"
    }
}
```

## Mapping example

```json
{
  "id": "<uuid>",
  "issuer": "<issuerDid>",
  "credentialSubject": {
    "consumerId": "<subjectDid>"
  },
  "issuanceDate": "<timestamp>",
  "expirationDate": "<timestamp-in:365d>"
}
```