# DataspaceParticipantCredential

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1"
  ],
  "type": [
    "VerifiableCredential",
    "DataspaceParticipantCredential"
  ],
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuer": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "name": "deltaDAO AG"
  },
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "credentialSubject": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "type": "DataspaceParticipant",
    "dataspaceId": "Pontus-X",
    "legalName": "deltaDAO AG",
    "website": "https://www.delta-dao.com",
    "legalAddress": {
      "countryCode": "DE",
      "streetAddress": "Katharinenstraße 30a",
      "postalCode": "20457",
      "locality": "Hamburg"
    }
  }
}
```

## Manifest

```json
{
  "claims": {
    "Legal Name": "$.credentialSubject.legalName",
    "Website": "$.credentialSubject.website",
    "Legal Address Country Code": "$.credentialSubject.legalAddress.countryCode",
    "Legal Address Street": "$.credentialSubject.legalAddress.streetAddress",
    "Legal Address Postal Code": "$.credentialSubject.legalAddress.postalCode",
    "Legal Address Locality": "$.credentialSubject.legalAddress.locality",
    "Dataspace ID": "$.credentialSubject.dataspaceId"
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