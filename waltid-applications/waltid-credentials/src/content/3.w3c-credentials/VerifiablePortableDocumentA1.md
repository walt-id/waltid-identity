# VerifiablePortableDocumentA1

```json
{
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "type": ["VerifiableCredential", "VerifiableAttestation", "VerifiablePortableDocumentA1"],
    "issuer": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "issued": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "credentialSubject": {
            "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
            "section1": {
                "personalIdentificationNumber": "1",
                "sex": "01",
                "surname": "Jane",
                "forenames": "Doe",
                "dateBirth": "1985-08-15",
                "nationalities": [
                    "BE"
                ],
                "stateOfResidenceAddress": {
                    "streetNo": "sss, nnn ",
                    "postCode": "ppp",
                    "town": "ccc",
                    "countryCode": "BE"
                },
                "stateOfStayAddress": {
                    "streetNo": "sss, nnn ",
                    "postCode": "ppp",
                    "town": "ccc",
                    "countryCode": "BE"
                }
            },
            "section2": {
                "memberStateWhichLegislationApplies": "DE",
                "startingDate": "2022-10-09",
                "endingDate": "2022-10-29",
                "certificateForDurationActivity": true,
                "determinationProvisional": false,
                "transitionRulesApplyAsEC8832004": false
            },
            "section3": {
                "postedEmployedPerson": false,
                "employedTwoOrMoreStates": false,
                "postedSelfEmployedPerson": true,
                "selfEmployedTwoOrMoreStates": true,
                "civilServant": true,
                "contractStaff": false,
                "mariner": false,
                "employedAndSelfEmployed": false,
                "civilAndEmployedSelfEmployed": true,
                "flightCrewMember": false,
                "exception": false,
                "exceptionDescription": "",
                "workingInStateUnder21": false
            },
            "section4": {
                "employee": false,
                "selfEmployedActivity": true,
                "nameBusinessName": "1",
                "registeredAddress": {
                    "streetNo": "1, 1 1",
                    "postCode": "1",
                    "town": "1",
                    "countryCode": "DE"
                }
            },
            "section5": {
                "noFixedAddress": true
            },
            "section6": {
                "name": "National Institute for the Social Security of the Self-employed (NISSE)",
                "address": {
                    "streetNo": "Quai de Willebroeck 35",
                    "postCode": "1000",
                    "town": "Bruxelles",
                    "countryCode": "BE"
                },
                "institutionID": "NSSIE/INASTI/RSVZ",
                "officeFaxNo": "",
                "officePhoneNo": "0800 12 018",
                "email": "info@rsvz-inasti.fgov.be",
                "date": "2022-10-28",
                "signature": "Official signature"
            }
        },
    "credentialSchema": {
        "id": "https://api-conformance.ebsi.eu/trusted-schemas-registry/v3/schemas/z5qB8tydkn3Xk3VXb15SJ9dAWW6wky1YEoVdGzudWzhcW",
        "type": "FullJsonSchemaValidator2021"
    }
}
```

## Manifest

```json
{
    "claims": {
        "Credential Subject ID": "$.credentialSubject.id",
        "Personal Identification Number": "$.credentialSubject.section1.personalIdentificationNumber",
        "Sex": "$.credentialSubject.section1.sex",
        "Surname": "$.credentialSubject.section1.surname",
        "Forenames": "$.credentialSubject.section1.forenames",
        "Date of Birth": "$.credentialSubject.section1.dateBirth",
        "Nationalities": "$.credentialSubject.section1.nationalities[0]",
        "State of Residence Address": "$.credentialSubject.section1.stateOfResidenceAddress",
        "State of Stay Address": "$.credentialSubject.section1.stateOfStayAddress",
        "Member State Legislation Applies": "$.credentialSubject.section2.memberStateWhichLegislationApplies",
        "Starting Date": "$.credentialSubject.section2.startingDate",
        "Ending Date": "$.credentialSubject.section2.endingDate",
        "Certificate for Duration of Activity": "$.credentialSubject.section2.certificateForDurationActivity",
        "Posted Self-Employed Person": "$.credentialSubject.section3.postedSelfEmployedPerson",
        "Self-Employed in Two or More States": "$.credentialSubject.section3.selfEmployedTwoOrMoreStates",
        "Civil Servant": "$.credentialSubject.section3.civilServant",
        "Civil and Employed Self-Employed": "$.credentialSubject.section3.civilAndEmployedSelfEmployed",
        "Self-Employed Activity": "$.credentialSubject.section4.selfEmployedActivity",
        "Business Name": "$.credentialSubject.section4.nameBusinessName",
        "Registered Address": "$.credentialSubject.section4.registeredAddress",
        "No Fixed Address": "$.credentialSubject.section5.noFixedAddress",
        "Institution Name": "$.credentialSubject.section6.name",
        "Institution Address": "$.credentialSubject.section6.address",
        "Institution ID": "$.credentialSubject.section6.institutionID",
        "Office Phone Number": "$.credentialSubject.section6.officePhoneNo",
        "Email": "$.credentialSubject.section6.email",
        "Date": "$.credentialSubject.section6.date",
        "Signature": "$.credentialSubject.section6.signature"
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
    "issuanceDate": "<timestamp-ebsi>",
    "issued": "<timestamp-ebsi>",
    "expirationDate": "<timestamp-ebsi-in:365d>"
}
```