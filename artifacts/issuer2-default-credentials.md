# Issuer2 default credential catalog

Decision for the **default issuer2** catalog (services image / live demo / docker-compose).

Opted-out credentials stay in the services config as comments so they can be re-enabled. Docker-compose does not ship them.

## Default catalog (14)

| Credential configuration | Format | Profile | Why |
| --- | --- | --- | --- |
| `eu.europa.ec.eudi.pid.1` | mso_mdoc | `eudiPidMdoc` | Primary EUDI PID (mdoc) |
| `urn:eudi:pid:1` | dc+sd-jwt | `eudiPidSdJwt` | Same PID in SD-JWT — teaches format choice |
| `org.iso.18013.5.1.mDL` | mso_mdoc | `isoMdl` | Canonical ISO mDL |
| `org.iso.23220.photoid.1` | mso_mdoc | `isoPhotoId` | Generic photo ID, distinct from PID and mDL |
| `eu.europa.ec.av.1` | mso_mdoc | `euAgeVerificationMdoc` | Age-over attestation |
| `identity_credential` | dc+sd-jwt | `identityCredentialSdJwt` | Generic SD-JWT identity that is not EUDI-specific |
| `urn:eudi:ehic:1` | dc+sd-jwt | `ehicSdJwt` | Health / benefits story |
| `urn:eu.europa.ec.eudi:cor:1` | dc+sd-jwt | `certificateOfResidenceSdJwt` | EUDI residence |
| `OpenBadgeCredential_jwt_vc_json` | jwt_vc_json | `openBadgeCredential` | Clean W3C VC example |
| `KycCredential_jwt_vc_json` | jwt_vc_json | `kycCredential` | Financial onboarding W3C VC |
| `HotelReservation_jwt_vc_json` | jwt_vc_json | `hotelReservation` | Everyday non-identity W3C VC |
| `ProofOfAddress_jwt_vc_json` | jwt_vc_json | `proofOfAddress` | Address proof without another government ID |
| `sca_payment_card_mso_mdoc` | mso_mdoc | `scaPaymentCardMdoc` | SCA payment-card demo with authorized transaction data |
| `emvco_dpc_mso_mdoc` | mso_mdoc | `emvcoDpcMdoc` | EMVCo Digital Payment Credential demo |

That is **14 cards**: 6 mdoc, 4 SD-JWT, 4 W3C VC.

## Commented out of services / removed from docker-compose

| Credential configuration | Profile | Why it is not default |
| --- | --- | --- |
| `identity_credential_haip` | `identityCredentialHaipSdJwt` | Protocol variant of `identity_credential` |
| `org.iso.18013.5.1.mDL.haip` | `isoMdlHaip` | Protocol variant of mDL |
| `org.iso.18013.5.1.mDL.aamva` | `isoMdlAamva` | Regional mDL; ISO remains the default |
| `at.gv.id-austria.2023.iso` | `idAustriaMdoc` | Country-specific |
| `com.google.wallet.idcard.1` | `googleIdCardMdoc` | Vendor-specific |
| `asit.tax-id-credential` | `taxIdCredentialSdJwt` | Niche unless a tax demo is requested |
| `urn:eu.europa.ec.eudi:por:1` | `powerOfRepresentationSdJwt` | Overlaps the residence / mandate story |
| `BankId_jwt_vc_json` | `bankId` | Overlaps KYC |
| `AlpsTourReservation_jwt_vc_json` | `alpsTourReservation` | Duplicate of hotel reservation |
| `PND91Credential_jwt_vc_json` | `pnd91Credential` | Unclear sample with no demo narrative |

HAIP metadata and profiles were already absent from docker-compose.

## How this is applied

- **Services default** (`waltid-services/waltid-issuer-api2/config`): opted-out objects are commented in place.
- **Docker-compose** (`docker-compose/issuer-api2/config`): the same objects are removed so the mounted catalog matches the live default.
- **Portal2 simple Issue** lists whatever the configured issuer advertises. Curate the issuer, not a second allow-list in the portal.
