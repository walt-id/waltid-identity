# org.multipaz.loyalty.1

## Source

- Dataset source: https://github.com/openwallet-foundation/multipaz

## Document Type

- `docType`: `org.multipaz.loyalty.1`
- `fields`: `7`

## Example JSON (from sample values)

```json
{
  "docType": "org.multipaz.loyalty.1",
  "credentialSubject": {
    "org.multipaz.loyalty.1": {
      "family_name": "Mustermann",
      "given_name": "Erika",
      "portrait": null,
      "membership_number": "24601",
      "tier": "basic",
      "issue_date": "2024-03-15",
      "expiry_date": "2028-09-01"
    }
  }
}
```

## Fields

- **family_name**
  - namespace: `org.multipaz.loyalty.1`
  - mandatory: `True`
  - sampleValue: `"Mustermann"`
- **given_name**
  - namespace: `org.multipaz.loyalty.1`
  - mandatory: `True`
  - sampleValue: `"Erika"`
- **portrait**
  - namespace: `org.multipaz.loyalty.1`
  - mandatory: `True`
  - sampleValue: `null`
- **membership_number**
  - namespace: `org.multipaz.loyalty.1`
  - mandatory: `False`
  - sampleValue: `"24601"`
- **tier**
  - namespace: `org.multipaz.loyalty.1`
  - mandatory: `False`
  - sampleValue: `"basic"`
- **issue_date**
  - namespace: `org.multipaz.loyalty.1`
  - mandatory: `True`
  - sampleValue: `"2024-03-15"`
- **expiry_date**
  - namespace: `org.multipaz.loyalty.1`
  - mandatory: `True`
  - sampleValue: `"2028-09-01"`
