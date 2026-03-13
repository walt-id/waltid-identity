# GaiaXCompliance

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
    "https://w3id.org/security/suites/jws-2020/v1",
    "https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#"
  ],
  "type": [
    "VerifiableCredential",
    "gx:Compliance"
  ],
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "credentialSubject": [
    {
      "type": "gx:compliance",
      "id": "https://wizard.lab.gaia-x.eu/api/credentials/2d37wbGvQzbAQ84yRouh2m2vBKkN8s5AfH9Q75HZRCUQmJW7yAVSNKzjJj6gcjE2mDNDUHCichXWdMH3S2c8AaDLm3kXmf5R8DTHekDGCWVoaGSWvdh6ybfoyLKPMWuvoPM6zK7cQSrR8GsjGaiX7uzEkcq6GsqhiUyczNkZ8RmoByDMujz64Q45k4U2Q4xpxWpG2qqJX6JA3oGaUNN8LAUPwuhgCPoXN4G23SL8Y9N3B46Jf4DnVEJuu9THoeqU6dDWioJdVid16nMdorSTE37P3S6r8N5mWrU3N9qcJEhHgSUQA1ikDctNffJjFQYexWvW6F2zCvXQXU9veDthxnaDGTfPeNn2Y5gcsLURsQu9MrZWxGGbosYJogn7X4bMR2H95WSKqL6FDeUoq7GtoDwBQNShZzmGKZdCwfPVKZzMQxjDLibxDWDV8g4NXf9RPMT2Ass7CDhe8nxrBYMe1EHPfLBGRPDmPsy9xEh1iz5fyKj2zc6o9g9KYrnkgjihrYwdNZW3M4cqti8zV2oqBw4PztksJGy4B8PT9wNqs4vSFGn98rShK539rDVZVDJiacW6f6drPmPZXScLSV9hsWKa1ysFe6A3hohmDQg?uid=f1c121b2-091e-4363-8a54-f2d070b04189",
      "gx:integrity": "sha256-7549decfb348bf6d342c3d2594c0ba33869c0b2b3065d0a197e899f0eb60baad",
      "gx:integrityNormalization": "RFC8785:JCS",
      "gx:version": "22.10",
      "gx:type": "gx:LegalParticipant"
    }
  ]
}
```

## Manifest

```json
{
    "claims": {
        "Gaia-X Compliance": "$.credentialSubject['gx:compliance']"
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