# PhotoIDCredential

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
    "https://domain.com/photoid.json"
  ],
  "id": "urn:uuid:123",
  "type": ["VerifiableCredential", "PhotoIDCredential"],
  "issuer": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION"
  },
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "credentialSubject": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "iso23220": {
      "family_name_unicode": "Doe",
      "given_name_unicode": "John",
      "birth_date": "1990-01-01",
      "portrait": "base64-image-data",
      "issue_date": "2025-01-01",
      "expiry_date": "2035-01-01",
      "issuing_authority_unicode": "Gov Authority",
      "issuing_country": "US",
      "age_over_18": true
    },
    "photoid": {
      "person_id": "PID123456",
      "birth_country": "US",
      "birth_state": "CA",
      "birth_city": "Los Angeles"
    },
    "dtc": {
      "dtc_dg1": "MRZDATA==",
      "dtc_dg2": "FACEIMAGE==",
      "dtc_sod": "SODDATA=="
    }
  }
}

```

## Manifest

```json
{
  "claims": {
    "Full Name": "$.credentialSubject.iso23220.given_name_unicode $.credentialSubject.iso23220.family_name_unicode",
    "Birth Date": "$.credentialSubject.iso23220.birth_date",
    "Issuing Country": "$.credentialSubject.iso23220.issuing_country",
    "Document Issue Date": "$.credentialSubject.iso23220.issue_date",
    "Document Expiry Date": "$.credentialSubject.iso23220.expiry_date",
    "Over 18": "$.credentialSubject.iso23220.age_over_18",
    "Person ID": "$.credentialSubject.photoid.person_id",
    "Birthplace": "$.credentialSubject.photoid.birth_city, $.credentialSubject.photoid.birth_state, $.credentialSubject.photoid.birth_country"
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