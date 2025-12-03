# Iso18013DriversLicenseCredential

## Verifiable Driver's License Vocabulary v0.1

This specification describes an experimental vocabulary for expressing a Verifiable Driver's License, which is intended to be fully conformant with the ISO 18013 Mobile Driver's License data model. [According https://w3c-ccg.github.io/vdl-vocab/]


```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
    "https://w3id.org/vdl/v1",
    "https://w3id.org/vdl/aamva/v1"
  ],
  "type": [
    "VerifiableCredential",
    "Iso18013DriversLicenseCredential"
  ],
  "issuer": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "name": "Utopia Department of Motor Vehicles",
    "url": "https://dmv.utopia.example/",
    "image": "https://dmv.utopia.example/logo.png"
  },
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "name": "Utopia Driver's License",
  "image": "data:image/jpeg;base64,iVBORw0KGgoAAAANSUhEUg...kSuQmCC",
  "description": "A license granting driving privileges in Utopia.",
  "credentialSubject": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "type": "LicensedDriver",
    "driversLicense": {
      "type": "Iso18013DriversLicense",
      "document_number": "542426814",
      "family_name": "TURNER",
      "given_name": "SUSAN",
      "portrait": "data:image/jpeg;base64,/9j/4AAQSkZJR...RSClooooP/2Q==",
      "birth_date": "1998-08-28",
      "issue_date": "2023-01-15T10:00:00-07:00",
      "expiry_date": "2028-08-27T12:00:00-06:00",
      "issuing_country": "UA",
      "issuing_authority": "UADMV",
      "driving_privileges": [{
        "codes": [{"code": "D"}],
        "vehicle_category_code": "D",
        "issue_date": "2019-01-01",
        "expiry_date": "2027-01-01"
      },
        {
          "codes": [{"code": "C"}],
          "vehicle_category_code": "C",
          "issue_date": "2019-01-01",
          "expiry_date": "2017-01-01"
        }],
      "un_distinguishing_sign": "UTA",
      "aamva_aka_suffix": "1ST",
      "sex": 2,
      "aamva_family_name_truncation": "N",
      "aamva_given_name_truncation": "N"
    }
  }
}
```

## Manifest

```json
{
    "claims": {
        "Document Number": "$.credentialSubject.driversLicense.document_number",
        "Family Name": "$.credentialSubject.driversLicense.family_name",
        "Given Name": "$.credentialSubject.driversLicense.given_name",
        "Birth Date": "$.credentialSubject.driversLicense.birth_date",
        "Issuing Country": "$.credentialSubject.driversLicense.issuing_country",
        "Issuing Authority": "$.credentialSubject.driversLicense.issuing_authority",
        "Driving Privileges (Vehicle Code)": "$.credentialSubject.driversLicense.driving_privileges[0].vehicle_category_code"
    }
}
```

## Mapping example

```json
{
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