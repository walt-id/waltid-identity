# PID - Person Identification Data (eu.europa.ec.eudi.pid.1)

This is an example of an EU Digital Identity Wallet Person Identification Data (PID) credential.

## Example PID Structure

```json
{
  "documentType": "eu.europa.ec.eudi.pid.1",
  "issuer": "https://eid.example.eu",
  "issuedAt": "2024-01-15T10:30:00Z",
  "expiresAt": "2034-01-15T10:30:00Z",
  "credentialSubject": {
    "familyName": "Müller",
    "givenName": "Anna",
    "birthName": "Schmidt",
    "birthDate": "1990-03-22",
    "birthPlace": "Berlin",
    "birthCountry": "DE",
    "nationality": "German",
    "gender": "female",
    "personalIdentifier": "DE123456789012345",
    "familyNameAtBirth": "Schmidt",
    "givenNameAtBirth": "Anna",
    "currentAddress": {
      "street": "Musterstraße 123",
      "postalCode": "10115",
      "city": "Berlin",
      "country": "DE"
    }
  }
}
```

## Available Claims

The following claims can be accessed:

- **Family Name**: `$.credentialSubject.familyName`
- **Given Name**: `$.credentialSubject.givenName`
- **Birth Name**: `$.credentialSubject.birthName`
- **Birth Date**: `$.credentialSubject.birthDate`
- **Birth Place**: `$.credentialSubject.birthPlace`
- **Birth Country**: `$.credentialSubject.birthCountry`
- **Nationality**: `$.credentialSubject.nationality`
- **Gender**: `$.credentialSubject.gender`
- **Personal Identifier**: `$.credentialSubject.personalIdentifier`
- **Family Name At Birth**: `$.credentialSubject.familyNameAtBirth`
- **Given Name At Birth**: `$.credentialSubject.givenNameAtBirth`
- **Current Address**: `$.credentialSubject.currentAddress`

## Usage

This credential follows the EU Digital Identity Wallet framework for person identification data, enabling secure digital identity verification across EU member states.
