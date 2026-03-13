# org.multipaz.payment.sca.1

## Source

- Dataset source: https://github.com/openwallet-foundation/multipaz

## Document Type

- `docType`: `org.multipaz.payment.sca.1`
- `fields`: `6`

## Example JSON (from sample values)

```json
{
  "docType": "org.multipaz.payment.sca.1",
  "credentialSubject": {
    "org.multipaz.payment.sca.1": {
      "issuer_name": "Utopia Bank",
      "payment_instrument_id": "pi-77AABBCC",
      "masked_account_reference": "****1234",
      "holder_name": "Erika Mustermann",
      "issue_date": "2024-03-15",
      "expiry_date": "2028-09-01"
    }
  }
}
```

## Fields

- **issuer_name**
  - namespace: `org.multipaz.payment.sca.1`
  - mandatory: `True`
  - sampleValue: `"Utopia Bank"`
- **payment_instrument_id**
  - namespace: `org.multipaz.payment.sca.1`
  - mandatory: `False`
  - sampleValue: `"pi-77AABBCC"`
- **masked_account_reference**
  - namespace: `org.multipaz.payment.sca.1`
  - mandatory: `True`
  - sampleValue: `"****1234"`
- **holder_name**
  - namespace: `org.multipaz.payment.sca.1`
  - mandatory: `True`
  - sampleValue: `"Erika Mustermann"`
- **issue_date**
  - namespace: `org.multipaz.payment.sca.1`
  - mandatory: `True`
  - sampleValue: `"2024-03-15"`
- **expiry_date**
  - namespace: `org.multipaz.payment.sca.1`
  - mandatory: `True`
  - sampleValue: `"2028-09-01"`
