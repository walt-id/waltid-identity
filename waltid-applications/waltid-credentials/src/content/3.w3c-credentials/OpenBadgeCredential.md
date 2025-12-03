# OpenBadgeCredential

## Credential example

```json
{
    "@context": ["https://www.w3.org/2018/credentials/v1", "https://purl.imsglobal.org/spec/ob/v3p0/context.json"],
    "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "type": ["VerifiableCredential", "OpenBadgeCredential"],
    "name": "JFF x vc-edu PlugFest 3 Interoperability",
    "issuer": {
        "type": ["Profile"],
        "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        "name": "Jobs for the Future (JFF)",
        "url": "https://www.jff.org/",
        "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
    },
    "issuanceDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "expirationDate": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
    "credentialSubject": {
        "id": "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        "type": ["AchievementSubject"],
        "achievement": {
            "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
            "type": ["Achievement"],
            "name": "JFF x vc-edu PlugFest 3 Interoperability",
            "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
            "criteria": {
                "type": "Criteria",
                "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
            },
            "image": {
                "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                "type": "Image"
            }
        }
    }
}
```

## Manifest

```json
{
    "claims": {
        "Achievement Name": "$.credentialSubject.achievement.name",
        "Achievement Description": "$.credentialSubject.achievement.description",
        "Criteria Narrative": "$.credentialSubject.achievement.criteria.narrative"
    }
}
```

## Mapping example

```json
{
    "id": "<uuid>",
    "issuer": {
        "id": "<issuerDid>"
    },
    "credentialSubject": {
        "id": "<subjectDid>"
    },
    "issuanceDate": "<timestamp>",
    "expirationDate": "<timestamp-in:365d>"
}
```
