# EducationalID

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
    "https://europa.eu/schemas/v-id/2020/v1",
    "https://europa.eu/schemas/eidas/2020/v1"
  ],
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "type": [
    "VerifiableCredential",
    "EducationalID"
  ],
  "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issued": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "credentialSubject": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "identifier": [
      {
        "schemeID": "European Student Identifier",
        "value": "urn:schac:personalUniqueCode:int:esi:math.example.edu:xxxxxxxxxx",
        "id": "urn:schac:personalUniqueCode:int:esi:university.eu:firstlast@email.eu"
      }
    ],
    "schacPersonalUniqueID": "urn:schac:personalUniqueID:int:passport:{COUNTRY_CODE}:{PASSPORT_CODE}",
    "commonName": "Frist Last",
    "displayName": "Frist Last",
    "firstName": "Frist",
    "familyName": "Ferreira",
    "dateOfBirth": "19730429",
    "schacHomeOrganization": " ",
    "mail": "first.last@university.eu",
    "eduPersonPrincipalName": "first.last@university.eu",
    "eduPersonPrimaryAffiliation": "student",
    "schacPersonalUniqueCode": "urn:schac:personalUniqueCode:int:esi:university.eu:firstlast@email.eu",
    "eduPersonAffiliation": [
      {
        "value": "student, staff"
      }
    ],
    "eduPersonScopedAffiliation": [
      {
        "value": "student@university.eu, staff@university.eu"
      }
    ],
    "eduPersonAssurance": [
      {
        "value": "student, staff"
      }
    ]
  }
}
```

## Manifest

```json
{
    "claims": {
        "First Name": "$.credentialSubject.firstName",
        "Family Name": "$.credentialSubject.familyName",
        "Date of Birth": "$.credentialSubject.dateOfBirth",
        "Email": "$.credentialSubject.mail",
        "Primary Affiliation": "$.credentialSubject.eduPersonPrimaryAffiliation",
        "Personal Unique ID": "$.credentialSubject.schacPersonalUniqueID",
        "Primary Affiliation": "$.credentialSubject.eduPersonPrimaryAffiliation"
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
  "issued": "<timestamp>"
}
```