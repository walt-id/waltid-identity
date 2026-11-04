# EU PID Credential (SD-JWT VC)

## Source

Dataset source: https://github.com/a-sit-plus/eu-pid-credential-sdjwt/

## SD-JWT Type (vct)

`urn:eudi:pid:1`

## Credential Data

Claim names follow the PID rulebook SD-JWT encoding (`birthdate`, `place_of_birth`, `nationalities`, `address`, `picture`, `date_of_issuance`, `date_of_expiry`). The mdoc encoding keeps the data identifiers such as `birth_date`.

```json
{
  "family_name": "Musterfrau",
  "given_name": "Anna Maria",
  "birthdate": "1971-09-01",
  "place_of_birth": {
    "country": "AT",
    "region": "Vienna",
    "locality": "Vienna"
  },
  "nationalities": ["AT", "CH"],
  "address": {
    "formatted": "Mariahilfer Straße 120/8, 1070 Wien, Austria",
    "country": "AT",
    "region": "Vienna",
    "locality": "Vienna",
    "postal_code": "1070",
    "street_address": "Mariahilfer Straße",
    "house_number": "120/8"
  },
  "personal_administrative_number": "9876543210",
  "picture": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z0i8AAAAASUVORK5CYII=",
  "birth_family_name": "Leitner",
  "birth_given_name": "Anna",
  "sex": 2,
  "email": "anna.musterfrau@example.at",
  "phone_number": "+4366412345678",
  "date_of_expiry": "2030-01-15",
  "issuing_authority": "Bundesministerium für Inneres",
  "document_number": "A01234567",
  "issuing_country": "AT",
  "issuing_jurisdiction": "AT-9",
  "location_status": "https://example.com/statuslists/pid/",
  "date_of_issuance": "2025-01-15",
  "trust_anchor": "https://wallet.example.at/trust/anchors/root-ca-2025.pem",
  "attestation_legal_category": "PID"
}
```