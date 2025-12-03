# mDL - Mobile Driving Licence (org.iso.18013.5.1.mDL)

This is an example of an ISO 18013-5 Mobile Driving Licence (mDL) credential.

## Example mDL Structure

```json
{
  "documentType": "org.iso.18013.5.1.mDL",
  "issuer": "https://dmv.example.gov",
  "issuedAt": "2024-01-15T10:30:00Z",
  "expiresAt": "2029-01-15T10:30:00Z",
  "credentialSubject": {
    "familyName": "Smith",
    "givenName": "John",
    "birthDate": "1985-06-15",
    "licenseNumber": "DL123456789",
    "licenseClass": "B",
    "issuingCountry": "US",
    "issuingState": "CA",
    "issuingAuthority": "Department of Motor Vehicles",
    "issueDate": "2024-01-15",
    "expiryDate": "2029-01-15",
    "restrictions": [],
    "endorsements": []
  }
}
```

## Available Claims

The following claims can be accessed:

- **Family Name**: `$.credentialSubject.familyName`
- **Given Name**: `$.credentialSubject.givenName`
- **Birth Date**: `$.credentialSubject.birthDate`
- **License Number**: `$.credentialSubject.licenseNumber`
- **License Class**: `$.credentialSubject.licenseClass`
- **Issuing Country**: `$.credentialSubject.issuingCountry`
- **Issuing State**: `$.credentialSubject.issuingState`
- **Issuing Authority**: `$.credentialSubject.issuingAuthority`
- **Issue Date**: `$.credentialSubject.issueDate`
- **Expiry Date**: `$.credentialSubject.expiryDate`
- **Restrictions**: `$.credentialSubject.restrictions`
- **Endorsements**: `$.credentialSubject.endorsements`

## Usage

This credential follows the ISO 18013-5 standard for mobile driving licences, enabling secure digital representation of driving privileges.
