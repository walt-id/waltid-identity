# DataConnectorServiceOffering

```json
{
  "@type": "DataConnectorServiceOffering",
  "@context": "https://w3id.org/gaia-x/development",
  "id": "iso-example-001",
  "name": "High-Performance Storage Service",
  "description": "Enterprise-grade block storage, SSD backed, globally redundant.",
  "providedBy": {
    "@type": "LegalPerson",
    "id": "legal-entity-provider-123",
    "name": "Global Cloud Storage Inc."
  },
  "serviceOfferingTermsAndConditions": [
    {
      "@type": "TermsAndConditions",
      "id": "toc-storage-001",
      "description": "Standard terms and conditions for High-Performance Storage Service",
      "effectiveDate": "2025-01-01"
    }
  ],
  "dataAccountExport": [
    {
      "@type": "DataAccountExport",
      "id": "dae-storage-001",
      "method": "API",
      "description": "Data export via RESTful API"
    }
  ],
  "aggregationOfResources": [
    "resource-ssd-pool-001",
    "resource-redundant-replica-east-001"
  ],
  "dependsOn": [
    {
      "@type": "ServiceOffering",
      "id": "iso-network-001",
      "name": "10Gbps Backbone Connectivity"
    }
  ],
  "servicePolicy": [
    {
      "@type": "AccessUsagePolicy",
      "id": "aup-storage-001",
      "policyDocument": "https://example.com/policies/storage-usage.pdf"
    }
  ],
  "dataProtectionRegime": [
    {
      "@type": "PersonalDataProtectionRegime",
      "id": "pdpr-eu-001",
      "jurisdiction": "EU",
      "description": "GDPR compliant data protection regime"
    }
  ],
  "dataPortability": [
    {
      "@type": "DataPortability",
      "id": "dp-storage-001",
      "format": "CSV",
      "description": "Data can be exported in CSV format"
    }
  ],
  "possiblePersonalDataTransfers": [
    {
      "@type": "DataTransfer",
      "id": "dt-storage-001",
      "destination": "US-East-1",
      "description": "Possible transfer of data to US-East-1 region under standard contractual clauses"
    }
  ],
  "requiredMeasures": [
    {
      "@type": "Measure",
      "id": "meas-encryption-001",
      "name": "At-rest Encryption",
      "description": "All stored data encrypted at rest using AES-256"
    }
  ],
  "cryptographicSecurityStandards": [
    {
      "@type": "CryptographicSecurityStandards",
      "id": "css-rsa-2048-001",
      "name": "RSA-2048",
      "description": "Encryption key length minimum 2048 bits"
    }
  ],
  "providerContactInformation": {
    "@type": "ContactInformation",
    "email": "support@globalcloudstorage.com",
    "telephone": "+1-800-555-1234",
    "url": "https://globalcloudstorage.com/support"
  },
  "keyword": [
    "block-storage",
  "SSD",
    "global-redundancy"
  ],
  "provisionType": "OnDemand",
  "endpoint": {
    "@type": "Endpoint",
    "id": "endpoint-storage-001",
    "url": "https://api.globalcloudstorage.com/v1",
    "protocol": "HTTPS"
  },
  "hostedOn": [
    "datacenter-us-east-1a",
    "availabilityZone-eu-west-1b"
  ],
  "serviceScope": "Global enterprise customers",
  "legalDocuments": [
    {
      "@type": "LegalDocument",
      "id": "ld-storage-sla-001",
      "title": "Service Level Agreement – High-Performance Storage",
      "url": "https://globalcloudstorage.com/sla/hpstorage.pdf",
      "effectiveDate": "2025-01-01"
    }
  ],
  "subContractors": [
    {
      "@type": "SubContractor",
      "id": "sub-cloud-infra-001",
      "name": "CloudInfraServices Ltd.",
      "role": "Infrastructure hosting subcontractor"
    }
  ],
  "customerInstructions": [
    {
      "@type": "CustomerInstructions",
      "id": "ci-storage-001",
      "instructions": "Use the API token to authenticate and mount volumes with read/write permissions."
    }
  ]
}
```