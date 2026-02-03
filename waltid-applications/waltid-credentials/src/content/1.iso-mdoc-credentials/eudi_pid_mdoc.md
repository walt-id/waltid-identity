# EUDI PID mDoc - Person Identification Data (eu.europa.ec.eudi.pid.1)

This is an EU Digital Identity Wallet Person Identification Data (PID) credential in mDoc format.

## Issuance Data (mdocData format)

```json
{
  "eu.europa.ec.eudi.pid.1": {
    "family_name": "Mustermann",
    "given_name": "Erika",
    "birth_date": "1964-08-12",
    "age_over_18": true,
    "age_over_21": true,
    "age_in_years": 60,
    "age_birth_year": 1964,
    "family_name_birth": "Gabler",
    "given_name_birth": "Erika",
    "birth_place": "Berlin",
    "birth_country": "DE",
    "birth_state": "Berlin",
    "birth_city": "Berlin",
    "resident_address": "Heidestraße 17",
    "resident_country": "DE",
    "resident_state": "Nordrhein-Westfalen",
    "resident_city": "Köln",
    "resident_postal_code": "51147",
    "resident_street": "Heidestraße 17",
    "resident_house_number": "17",
    "gender": "female",
    "nationality": "DE",
    "issuance_date": "2024-01-15",
    "expiry_date": "2034-01-15",
    "issuing_authority": "Bundesdruckerei",
    "document_number": "123456789",
    "administrative_number": "DE-123456789",
    "issuing_country": "DE",
    "issuing_jurisdiction": "DE-NW"
  }
}
```

## Claims Description

The EUDI PID mDoc uses the `eu.europa.ec.eudi.pid.1` namespace with the following claims:

### Mandatory Claims
- **family_name**: Current family name
- **given_name**: Current first name(s)
- **birth_date**: Date of birth (ISO 8601)

### Optional Claims
- **age_over_18/21**: Age verification attestations
- **birth_place/country/state/city**: Birth location details
- **resident_***: Current residence information
- **nationality**: ISO 3166-1 alpha-2 country code

## Usage

This credential follows the ISO 18013-5 mDoc format as specified in the EU Digital Identity Wallet Architecture and Reference Framework (ARF).
