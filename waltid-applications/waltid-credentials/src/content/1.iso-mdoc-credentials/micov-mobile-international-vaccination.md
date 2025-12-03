# mICOV - Mobile International Certificate of Vaccination (org.micov.1)

This is an example of a Mobile International Certificate of Vaccination (mICOV) credential.

## Example mICOV Structure

```json
{
  "documentType": "org.micov.1",
  "issuer": "https://health.example.org",
  "issuedAt": "2024-01-15T10:30:00Z",
  "expiresAt": "2029-01-15T10:30:00Z",
  "credentialSubject": {
    "person": {
      "familyName": "Wilson",
      "givenName": "Emma",
      "birthDate": "1992-04-12",
      "nationality": "British",
      "passportNumber": "GB123456789"
    },
    "vaccinations": [
      {
        "vaccine": {
          "name": "COVID-19 Vaccine",
          "manufacturer": "Pfizer-BioNTech",
          "batchNumber": "PF123456",
          "manufacturingDate": "2023-12-01"
        },
        "administration": {
          "date": "2024-01-10",
          "doseNumber": 1,
          "totalDoses": 2,
          "healthcareProvider": "City Medical Center",
          "country": "GB",
          "lotNumber": "LOT456789"
        },
        "validity": {
          "validFrom": "2024-01-10",
          "validUntil": "2029-01-10"
        }
      }
    ],
    "issuingAuthority": "National Health Service",
    "issuingCountry": "GB"
  }
}
```

## Available Claims

The following claims can be accessed:

- **Person Name**: `$.credentialSubject.person.familyName`, `$.credentialSubject.person.givenName`
- **Birth Date**: `$.credentialSubject.person.birthDate`
- **Nationality**: `$.credentialSubject.person.nationality`
- **Passport Number**: `$.credentialSubject.person.passportNumber`
- **Vaccine Name**: `$.credentialSubject.vaccinations[0].vaccine.name`
- **Manufacturer**: `$.credentialSubject.vaccinations[0].vaccine.manufacturer`
- **Batch Number**: `$.credentialSubject.vaccinations[0].vaccine.batchNumber`
- **Manufacturing Date**: `$.credentialSubject.vaccinations[0].vaccine.manufacturingDate`
- **Administration Date**: `$.credentialSubject.vaccinations[0].administration.date`
- **Dose Number**: `$.credentialSubject.vaccinations[0].administration.doseNumber`
- **Total Doses**: `$.credentialSubject.vaccinations[0].administration.totalDoses`
- **Healthcare Provider**: `$.credentialSubject.vaccinations[0].administration.healthcareProvider`
- **Country**: `$.credentialSubject.vaccinations[0].administration.country`
- **Lot Number**: `$.credentialSubject.vaccinations[0].administration.lotNumber`
- **Valid From**: `$.credentialSubject.vaccinations[0].validity.validFrom`
- **Valid Until**: `$.credentialSubject.vaccinations[0].validity.validUntil`
- **Issuing Authority**: `$.credentialSubject.issuingAuthority`
- **Issuing Country**: `$.credentialSubject.issuingCountry`

## Usage

This credential follows the mICOV standard for mobile international certificates of vaccination, enabling secure digital representation of vaccination records for international travel and health verification.
