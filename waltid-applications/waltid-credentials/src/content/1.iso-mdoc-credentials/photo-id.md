# Photo ID (org.iso.23220.photoid.1)

This is an example of an ISO 23220 Photo ID credential.

## Example Photo ID Structure

```json
{
  "documentType": "org.iso.23220.photoid.1",
  "issuer": "https://government.example.org",
  "issuedAt": "2024-01-15T10:30:00Z",
  "expiresAt": "2034-01-15T10:30:00Z",
  "credentialSubject": {
    "familyName": "Johnson",
    "givenName": "Sarah",
    "birthDate": "1988-11-08",
    "nationality": "Canadian",
    "gender": "female",
    "personalIdentifier": "CA123456789",
    "documentNumber": "PH123456789",
    "issuingCountry": "CA",
    "issuingAuthority": "Government of Canada",
    "issueDate": "2024-01-15",
    "expiryDate": "2034-01-15",
    "photoHash": "sha256-hash-of-photo-data",
    "biometricData": {
      "faceTemplate": "base64-encoded-face-template",
      "fingerprintTemplates": []
    }
  }
}
```

## Available Claims

The following claims can be accessed:

- **Family Name**: `$.credentialSubject.familyName`
- **Given Name**: `$.credentialSubject.givenName`
- **Birth Date**: `$.credentialSubject.birthDate`
- **Nationality**: `$.credentialSubject.nationality`
- **Gender**: `$.credentialSubject.gender`
- **Personal Identifier**: `$.credentialSubject.personalIdentifier`
- **Document Number**: `$.credentialSubject.documentNumber`
- **Issuing Country**: `$.credentialSubject.issuingCountry`
- **Issuing Authority**: `$.credentialSubject.issuingAuthority`
- **Issue Date**: `$.credentialSubject.issueDate`
- **Expiry Date**: `$.credentialSubject.expiryDate`
- **Photo Hash**: `$.credentialSubject.photoHash`
- **Biometric Data**: `$.credentialSubject.biometricData`

## Usage

This credential follows the ISO 23220 standard for photo identification documents, enabling secure digital representation of identity with biometric verification capabilities.
