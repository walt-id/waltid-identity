# TaxCredential

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1"
  ],
  "id": "urn:uuid:4e7f3c21-5d8b-4f2a-9b11-1c2d3e4f5a67",
  "type": [
    "VerifiableCredential",
    "TaxCredential"
  ],
  "issuer": "did:gov:US:irs",
  "issuanceDate": "2025-04-15T14:00:00Z",
  "expirationDate": "2026-04-14T23:59:59Z",
  "evidence": [
    {
      "type": "TaxAssessmentRecord",
      "assessmentReference": "IRS-2024-5087321",
      "assessedAt": "2025-04-05",
      "source": "Internal Revenue Service Masterfile"
    }
  ],
  "credentialSubject": {
    "id": "did:wallet:us-7890abcd",
    "person": {
      "givenName": "John",
      "familyName": "Doe",
      "dateOfBirth": "1988-06-22",
      "tin": "US-98-7654321",
      "taxResidency": {
        "countryCode": "US"
      },
      "address": {
        "street": "742 Evergreen Terrace",
        "city": "Springfield",
        "postalCode": "62704",
        "countryCode": "US"
      }
    },
    "assessmentSummary": {
      "taxYear": 2024,
      "filingStatus": "single",
      "incomeStabilityYears": 4,
      "employmentStatus": "employee",
      "employerCount": 1,
      "incomeBreakdown": {
        "employmentIncome": {
          "amount": 87500,
          "currency": "USD"
        },
        "selfEmploymentIncome": {
          "amount": 4500,
          "currency": "USD"
        },
        "capitalIncome": {
          "amount": 2500,
          "currency": "USD"
        }
      },
      "grossIncomeTotal": {
        "amount": 94500,
        "currency": "USD"
      },
      "deductionsTotal": {
        "amount": 12500,
        "currency": "USD"
      },
      "taxableIncome": {
        "amount": 82000,
        "currency": "USD"
      },
      "taxDueTotal": {
        "amount": 15200,
        "currency": "USD"
      },
      "withholdingPaid": {
        "amount": 15000,
        "currency": "USD"
      },
      "balanceOrRefund": {
        "amount": 200,
        "currency": "USD",
        "direction": "owed"
      },
      "socialContributionsPaid": {
        "amount": 3100,
        "currency": "USD"
      }
    },
    "clearance": {
      "status": "inGoodStanding",
      "asOf": "2025-04-05"
    },
    "derivedForLending": {
      "annualNetIncomeEstimate": {
        "amount": 67800,
        "currency": "USD"
      },
      "monthlyNetIncomeEstimate": {
        "amount": 5650,
        "currency": "USD"
      },
      "incomeVerificationConfidence": "taxAuthorityVerified"
    }
  }
}
```

## Manifest

```json
{
  "claims": {
    "Taxpayer ID": "$.credentialSubject.person.tin",
    "Given Name": "$.credentialSubject.person.givenName",
    "Family Name": "$.credentialSubject.person.familyName",
    "Date of Birth": "$.credentialSubject.person.dateOfBirth",
    "Tax Residency Country": "$.credentialSubject.person.taxResidency.countryCode",
    "Currency": "$.credentialSubject.assessmentSummary.grossIncomeTotal.currency",
    "Street Address": "$.credentialSubject.person.address.street",
    "City": "$.credentialSubject.person.address.city",
    "Postal Code": "$.credentialSubject.person.address.postalCode",
    "Country": "$.credentialSubject.person.address.countryCode",
    "Tax Year": "$.credentialSubject.assessmentSummary.taxYear",
    "Filing Status": "$.credentialSubject.assessmentSummary.filingStatus",
    "Income Stability Years": "$.credentialSubject.assessmentSummary.incomeStabilityYears",
    "Employment Status": "$.credentialSubject.assessmentSummary.employmentStatus",
    "Employer Count": "$.credentialSubject.assessmentSummary.employerCount",
    "Employment Income Amount": "$.credentialSubject.assessmentSummary.incomeBreakdown.employmentIncome.amount",
    "Self Employment Income Amount": "$.credentialSubject.assessmentSummary.incomeBreakdown.selfEmploymentIncome.amount",
    "Capital Income Amount": "$.credentialSubject.assessmentSummary.incomeBreakdown.capitalIncome.amount",
    "Gross Income Amount": "$.credentialSubject.assessmentSummary.grossIncomeTotal.amount",
    "Deductions Amount": "$.credentialSubject.assessmentSummary.deductionsTotal.amount",
    "Taxable Income Amount": "$.credentialSubject.assessmentSummary.taxableIncome.amount",
    "Tax Due Amount": "$.credentialSubject.assessmentSummary.taxDueTotal.amount",
    "Withholding Paid Amount": "$.credentialSubject.assessmentSummary.withholdingPaid.amount",
    "Balance or Refund Amount": "$.credentialSubject.assessmentSummary.balanceOrRefund.amount",
    "Social Contributions Amount": "$.credentialSubject.assessmentSummary.socialContributionsPaid.amount",
    "Balance or Refund Direction": "$.credentialSubject.assessmentSummary.balanceOrRefund.direction",
    "Clearance Status": "$.credentialSubject.clearance.status",
    "Clearance As Of": "$.credentialSubject.clearance.asOf",
    "Annual Net Income Amount": "$.credentialSubject.derivedForLending.annualNetIncomeEstimate.amount",
    "Monthly Net Income Amount": "$.credentialSubject.derivedForLending.monthlyNetIncomeEstimate.amount",
    "Income Verification Confidence": "$.credentialSubject.derivedForLending.incomeVerificationConfidence"
  }
}
```

## Mapping example

```json
{
  "id": "<uuid>",
  "issuer": "<issuerDid>",
  "credentialSubject": {
    "id": "<subjectDid>"
  },
  "issuanceDate": "<timestamp>",
  "expirationDate": "<timestamp-in:365d>",
  "evidence": [
    {
      "assessmentReference": "<assessmentReference>",
      "assessedAt": "<date>",
      "source": "<source>"
    }
  ]
}
```
