# org.iso.18013.5.1.mDL

## Source

- Dataset source: https://github.com/openwallet-foundation/multipaz

## Document Type

- `docType`: `org.iso.18013.5.1.mDL`
- `fields`: `63`

## Example JSON (from sample values)

```json
{
  "docType": "org.iso.18013.5.1.mDL",
  "credentialSubject": {
    "org.iso.18013.5.1": {
      "family_name": "Mustermann",
      "given_name": "Erika",
      "birth_date": "1971-09-01",
      "issue_date": "2024-03-15",
      "expiry_date": "2028-09-01",
      "issuing_country": "US",
      "issuing_authority": "Utopia Department of Motor Vehicles",
      "document_number": "987654321",
      "portrait": null,
      "driving_privileges": null,
      "un_distinguishing_sign": "UTO",
      "administrative_number": "123456789",
      "sex": 2,
      "height": 175,
      "weight": 68,
      "eye_colour": "blue",
      "hair_colour": "blond",
      "birth_place": "Sample City",
      "resident_address": "Sample Street 123, 12345 Sample City, Sample State, Utopia",
      "portrait_capture_date": "2020-03-14",
      "age_in_years": 54,
      "age_birth_year": 1971,
      "age_over_13": true,
      "age_over_16": true,
      "age_over_18": true,
      "age_over_21": true,
      "age_over_25": true,
      "age_over_60": false,
      "age_over_62": false,
      "age_over_65": false,
      "age_over_68": false,
      "issuing_jurisdiction": "State of Utopia",
      "nationality": "ZZ",
      "resident_city": "Sample City",
      "resident_state": "Sample State",
      "resident_postal_code": "12345",
      "resident_country": "ZZ",
      "family_name_national_character": "ÐÐ°Ð±ÑÐ°Ðº",
      "given_name_national_character": "ÐÑÐ¸ÐºÐ°",
      "signature_usual_mark": null,
      "biometric_template_face": null,
      "biometric_template_finger": null,
      "biometric_template_signature_sign": null,
      "biometric_template_iris": null
    },
    "org.iso.18013.5.1.aamva": {
      "domestic_driving_privileges": null,
      "name_suffix": "Jr III",
      "organ_donor": null,
      "veteran": null,
      "family_name_truncation": "N",
      "given_name_truncation": "N",
      "aka_family_name": "Musstermensch",
      "aka_given_name": "Erica",
      "aka_suffix": "Ica",
      "weight_range": null,
      "race_ethnicity": "W",
      "DHS_compliance": "F",
      "DHS_temporary_lawful_status": null,
      "EDL_credential": null,
      "resident_county": "037",
      "hazmat_endorsement_expiration_date": "2028-09-01",
      "sex": 2,
      "audit_information": "",
      "aamva_version": null
    }
  }
}
```

## Fields

- **family_name**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `True`
  - sampleValue: `"Mustermann"`
- **given_name**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `True`
  - sampleValue: `"Erika"`
- **birth_date**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `True`
  - sampleValue: `"1971-09-01"`
- **issue_date**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `True`
  - sampleValue: `"2024-03-15"`
- **expiry_date**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `True`
  - sampleValue: `"2028-09-01"`
- **issuing_country**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `True`
  - sampleValue: `"US"`
- **issuing_authority**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `True`
  - sampleValue: `"Utopia Department of Motor Vehicles"`
- **document_number**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `True`
  - sampleValue: `"987654321"`
- **portrait**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `True`
  - sampleValue: `null`
- **driving_privileges**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `True`
  - sampleValue: `null`
- **un_distinguishing_sign**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `True`
  - sampleValue: `"UTO"`
- **administrative_number**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `"123456789"`
- **sex**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `2`
- **height**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `175`
- **weight**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `68`
- **eye_colour**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `"blue"`
- **hair_colour**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `"blond"`
- **birth_place**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `"Sample City"`
- **resident_address**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `"Sample Street 123, 12345 Sample City, Sample State, Utopia"`
- **portrait_capture_date**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `"2020-03-14"`
- **age_in_years**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `54`
- **age_birth_year**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `1971`
- **age_over_13**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `true`
- **age_over_16**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `true`
- **age_over_18**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `true`
- **age_over_21**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `true`
- **age_over_25**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `true`
- **age_over_60**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `false`
- **age_over_62**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `false`
- **age_over_65**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `false`
- **age_over_68**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `false`
- **issuing_jurisdiction**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `"State of Utopia"`
- **nationality**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `"ZZ"`
- **resident_city**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `"Sample City"`
- **resident_state**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `"Sample State"`
- **resident_postal_code**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `"12345"`
- **resident_country**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `"ZZ"`
- **family_name_national_character**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `"ÐÐ°Ð±ÑÐ°Ðº"`
- **given_name_national_character**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `"ÐÑÐ¸ÐºÐ°"`
- **signature_usual_mark**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `null`
- **domestic_driving_privileges**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `True`
  - sampleValue: `null`
- **name_suffix**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `False`
  - sampleValue: `"Jr III"`
- **organ_donor**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `False`
  - sampleValue: `null`
- **veteran**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `False`
  - sampleValue: `null`
- **family_name_truncation**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `True`
  - sampleValue: `"N"`
- **given_name_truncation**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `True`
  - sampleValue: `"N"`
- **aka_family_name**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `False`
  - sampleValue: `"Musstermensch"`
- **aka_given_name**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `False`
  - sampleValue: `"Erica"`
- **aka_suffix**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `False`
  - sampleValue: `"Ica"`
- **weight_range**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `False`
  - sampleValue: `null`
- **race_ethnicity**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `False`
  - sampleValue: `"W"`
- **DHS_compliance**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `False`
  - sampleValue: `"F"`
- **DHS_temporary_lawful_status**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `False`
  - sampleValue: `null`
- **EDL_credential**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `False`
  - sampleValue: `null`
- **resident_county**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `False`
  - sampleValue: `"037"`
- **hazmat_endorsement_expiration_date**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `True`
  - sampleValue: `"2028-09-01"`
- **sex**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `True`
  - sampleValue: `2`
- **biometric_template_face**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `null`
- **biometric_template_finger**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `null`
- **biometric_template_signature_sign**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `null`
- **biometric_template_iris**
  - namespace: `org.iso.18013.5.1`
  - mandatory: `False`
  - sampleValue: `null`
- **audit_information**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `False`
  - sampleValue: `""`
- **aamva_version**
  - namespace: `org.iso.18013.5.1.aamva`
  - mandatory: `True`
  - sampleValue: `null`
