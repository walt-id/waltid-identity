# EU PID Credential (SD-JWT VC)

## Source

Dataset source: https://github.com/a-sit-plus/eu-pid-credential-sdjwt/

## SD-JWT Type (vct)

`urn:eudi:pid:1`

## Credential Data

```json
{
  "family_name": "Musterfrau",
  "given_name": "Anna Maria",
  "birth_date": "1971-09-01",
  "birth_place": {
    "country" : "Austria",
    "region" : "Vienna",
    "locality" : "Vienna"
  },
  "nationality": [
    "AT",
    "CH"
  ],
  "resident_address": "Mariahilfer Straße 120/8, 1070 Wien, Austria",
  "resident_country": "AT",
  "resident_state": "Vienna",
  "resident_city": "Vienna",
  "resident_postal_code": "1070",
  "resident_street": "Mariahilfer Straße",
  "resident_house_number": "120/8",
  "personal_administrative_number": "9876543210",
  "portrait": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z0i8AAAAASUVORK5CYII=",
  "family_name_birth": "Leitner",
  "given_name_birth": "Anna",
  "sex": 2,
  "email_address": "anna.musterfrau@example.at",
  "mobile_phone_number": "+4366412345678",
  "expiry_date": "2030-01-15T09:30:00Z",
  "issuing_authority": "Bundesministerium für Inneres",
  "document_number": "PID-AT-2025-00018427",
  "issuing_country": "AT",
  "document_number": "A01234567",
  "issuing_jurisdiction": "AT-9",
  "location_status": "https://example.com/statuslists/pid/",
  "issuance_date": "2025-01-15T09:30:00Z",
  "trust_anchor": "https://wallet.example.at/trust/anchors/root-ca-2025.pem",
  "attestation_legal_category": "PID"
}
```