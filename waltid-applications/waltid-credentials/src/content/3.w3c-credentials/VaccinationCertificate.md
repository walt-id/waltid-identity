# VaccinationCertificate

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1"
  ],
  "credentialSchema": {
    "id": "https://raw.githubusercontent.com/walt-id/waltid-ssikit-vclib/master/src/test/resources/schemas/VerifiableVaccinationCertificate.json",
    "type": "JsonSchemaValidator2018"
  },
  "credentialStatus": {
    "id": "https://essif.europa.eu/status/covidvaccination#392ac7f6-399a-437b-a268-4691ead8f176",
    "type": "CredentialStatusList2020"
  },
  "credentialSubject": {
    "dateOfBirth": "1993-04-08",
    "familyName": "DOE",
    "givenNames": "Jane",
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "personIdentifier": "optional The type of identifier and identifier of the person, according to the policies applicable in each country. Examples are citizen ID and/or document number (ID- card/passport) or identifier within the health system/IIS/e-registry.",
    "personSex": "optional",
    "uniqueCertificateIdentifier": "UVCI0904008084H",
    "vaccinationProphylaxisInformation": [
      {
        "administeringCentre": "Name/code of administering centre or a health authority responsible for the vaccination event",
        "batchNumber": "optional 1234",
        "countryOfVaccination": "DE",
        "dateOfVaccination": "2021-02-12",
        "diseaseOrAgentTargeted": {
          "code": "840539006",
          "system": "2.16.840.1.113883. 6.96",
          "version": "2021-01-31"
        },
        "doseNumber": "1",
        "marketingAuthorizationHolder": "Example Vaccine Manufacturing Company",
        "nextVaccinationDate": "optional - 2021-03-28",
        "totalSeriesOfDoses": "2",
        "vaccineMedicinalProduct": "VACCINE concentrate for dispersion for injection",
        "vaccineOrProphylaxis": "1119349007 COVID-19 example vaccine"
      }
    ]
  },
  "evidence": {
    "documentPresence": [
      "Physical"
    ],
    "evidenceDocument": [
      "Passport"
    ],
    "id": "https://essif.europa.eu/tsr-va/evidence/f2aeec97-fc0d-42bf-8ca7-0548192d5678",
    "subjectPresence": "Physical",
    "type": [
      "DocumentVerification"
    ],
    "verifier": "did:ebsi:2962fb784df61baa267c8132497539f8c674b37c1244a7a"
  },
  "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
  "type": [
    "VerifiableCredential",
    "VerifiableAttestation",
    "VaccinationCertificate"
  ]
}
```

## Manifest

```json
{
    "claims": {
        "Credential Subject ID": "$.credentialSubject.id",
        "Date of Birth": "$.credentialSubject.dateOfBirth",
        "Family Name": "$.credentialSubject.familyName",
        "Given Names": "$.credentialSubject.givenNames",
        "Person Identifier": "$.credentialSubject.personIdentifier",
        "Unique Certificate Identifier": "$.credentialSubject.uniqueCertificateIdentifier",
        "Administering Centre": "$.credentialSubject.vaccinationProphylaxisInformation[0].administeringCentre",
        "Batch Number": "$.credentialSubject.vaccinationProphylaxisInformation[0].batchNumber",
        "Country of Vaccination": "$.credentialSubject.vaccinationProphylaxisInformation[0].countryOfVaccination",
        "Date of Vaccination": "$.credentialSubject.vaccinationProphylaxisInformation[0].dateOfVaccination",
        "Disease or Agent Targeted": "$.credentialSubject.vaccinationProphylaxisInformation[0].diseaseOrAgentTargeted.code",
        "Dose Number": "$.credentialSubject.vaccinationProphylaxisInformation[0].doseNumber",
        "Marketing Authorization Holder": "$.credentialSubject.vaccinationProphylaxisInformation[0].marketingAuthorizationHolder",
        "Next Vaccination Date": "$.credentialSubject.vaccinationProphylaxisInformation[0].nextVaccinationDate",
        "Total Series of Doses": "$.credentialSubject.vaccinationProphylaxisInformation[0].totalSeriesOfDoses",
        "Vaccine Medicinal Product": "$.credentialSubject.vaccinationProphylaxisInformation[0].vaccineMedicinalProduct",
        "Vaccine or Prophylaxis": "$.credentialSubject.vaccinationProphylaxisInformation[0].vaccineOrProphylaxis"
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
    "expirationDate": "<timestamp-in:365d>"
}
```