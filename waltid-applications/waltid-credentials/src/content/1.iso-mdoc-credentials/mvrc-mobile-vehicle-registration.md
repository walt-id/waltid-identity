# mVRC - Mobile Vehicle Registration Certificate (org.iso.7367.1.mVRC)

This is an example of an ISO 7367 Mobile Vehicle Registration Certificate (mVRC) credential.

## Example mVRC Structure

```json
{
  "documentType": "org.iso.7367.1.mVRC",
  "issuer": "https://dmv.example.gov",
  "issuedAt": "2024-01-15T10:30:00Z",
  "expiresAt": "2025-01-15T10:30:00Z",
  "credentialSubject": {
    "vehicle": {
      "make": "Toyota",
      "model": "Camry",
      "year": "2023",
      "vin": "1HGBH41JXMN109186",
      "bodyType": "Sedan",
      "fuelType": "Hybrid",
      "engineSize": "2.5L",
      "transmission": "Automatic"
    },
    "owner": {
      "familyName": "Davis",
      "givenName": "Michael",
      "address": "123 Main St, Anytown, ST 12345"
    },
    "registration": {
      "registrationNumber": "ABC123",
      "state": "CA",
      "issueDate": "2024-01-15",
      "expiryDate": "2025-01-15",
      "registrationType": "Standard",
      "fees": {
        "registrationFee": 150.00,
        "emissionsFee": 25.00
      }
    }
  }
}
```

## Available Claims

The following claims can be accessed:

- **Vehicle Make**: `$.credentialSubject.vehicle.make`
- **Vehicle Model**: `$.credentialSubject.vehicle.model`
- **Vehicle Year**: `$.credentialSubject.vehicle.year`
- **VIN**: `$.credentialSubject.vehicle.vin`
- **Body Type**: `$.credentialSubject.vehicle.bodyType`
- **Fuel Type**: `$.credentialSubject.vehicle.fuelType`
- **Engine Size**: `$.credentialSubject.vehicle.engineSize`
- **Transmission**: `$.credentialSubject.vehicle.transmission`
- **Owner Name**: `$.credentialSubject.owner.familyName`, `$.credentialSubject.owner.givenName`
- **Owner Address**: `$.credentialSubject.owner.address`
- **Registration Number**: `$.credentialSubject.registration.registrationNumber`
- **State**: `$.credentialSubject.registration.state`
- **Issue Date**: `$.credentialSubject.registration.issueDate`
- **Expiry Date**: `$.credentialSubject.registration.expiryDate`
- **Registration Type**: `$.credentialSubject.registration.registrationType`
- **Fees**: `$.credentialSubject.registration.fees`

## Usage

This credential follows the ISO 7367 standard for mobile vehicle registration certificates, enabling secure digital representation of vehicle ownership and registration information.
