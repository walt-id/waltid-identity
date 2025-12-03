# EnrollmentCredential

```json
{
  "credentialData": {
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "@context": [
      "https://www.w3.org/2018/credentials/v1"
    ],
    "credentialSubject": {
      "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
      "wiserID": "10234567",
      "name": "Christine Schmidt",
      "birthDate": "1984-04-15",
      "address": {
        "streetAddress": "123 Main St",
        "postalCode": "82001",
        "addressLocality": "Cheyenne",
        "addressRegion": "WY",
        "addressCountry": "US"
      },
      "program": "NA1-2024",
      "enrollmentStatus": "Enrolled",
      "startDate": "2024-09-01",
      "expectedGraduationDate": "2028-06-30",
      "academicYear": "2024/2025",
      "currentSemester": "Spring 2025"
    },
    "issuer": {
      "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
      "name": "Eastern Wyoming College"
    },
    "type": [
      "VerifiableCredential",
      "EnrollmentCredential"
    ],
    "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION"
  }
}
```

## Manifest

```json
{
    "claims": {
        "Wiser ID": "$.credentialSubject.wiserID",        
        "Name": "$.credentialSubject.name",
        "Date of Birth": "$.credentialSubject.birthDate",
        "Street Address": "$.credentialSubject.address.streetAddress",
        "Postal Code": "$.credentialSubject.address.postalCode",
        "Program": "$.credentialSubject.program",
        "Enrollment Status": "$.credentialSubject.enrollmentStatus",
        "Start Date": "$.credentialSubject.startDate",
        "Expected Graduation Date": "$.credentialSubject.expectedGraduationDate",
        "Academic Year": "$.credentialSubject.academicYear",
        "Current Semester": "$.credentialSubject.currentSemester"
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