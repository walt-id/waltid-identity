package id.walt.issuer2.controller.openapi

import id.walt.issuer2.controller.dto.CredentialOfferCreateRequest
import id.walt.issuer2.controller.dto.CredentialOfferCreateResponse
import id.walt.issuer2.controller.dto.CredentialOfferRuntimeOverrides
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.CredentialOfferRequest
import id.walt.openid4vci.offers.CredentialOfferValueMode
import id.walt.openid4vci.offers.IssuerStateMode
import id.walt.openid4vci.offers.TxCode
import id.walt.sdjwt.SDMap
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object Issuer2RequestExamples {
    private val PROVIDED_TX_CODE = TxCode(
        inputMode = "numeric",
        length = 6,
        description = "Enter the PIN shown by the issuer",
    )

    private val GENERATED_TX_CODE = TxCode(
        inputMode = "numeric",
        length = 6,
        description = "Enter the generated PIN shown by the issuer",
    )

    val PROFILE_PRE_AUTHORIZED_OFFER = CredentialOfferCreateRequest(
        profileId = W3C_PROFILE_ID,
        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
        issuerStateMode = IssuerStateMode.OMIT,
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE = PROFILE_PRE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_BY_VALUE = PROFILE_PRE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_VALUE,
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_WITH_PROVIDED_TX_CODE = PROFILE_PRE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
        txCode = PROVIDED_TX_CODE,
        txCodeValue = PROVIDED_TX_CODE_VALUE,
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_WITH_GENERATED_TX_CODE = PROFILE_PRE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
        txCode = GENERATED_TX_CODE,
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_WITH_2_MIN_EXPIRY = PROFILE_PRE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
        expiresInSeconds = 120L,
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_WITHOUT_EXPIRY = PROFILE_PRE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
        expiresInSeconds = -1L,
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_WITH_CREDENTIAL_DATA_OVERRIDE = PROFILE_PRE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
        runtimeOverrides = CredentialOfferRuntimeOverrides(
            subjectId = "did:key:z6MkiY...",
            credentialData = buildJsonObject {
                putJsonObject("credentialSubject") {
                    put("givenName", "Jane")
                    put("familyName", "Doe")
                    put("degree", "Computer Science")
                }
            },
        ),
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_WITH_SELECTIVE_DISCLOSURE_OVERRIDE = PROFILE_PRE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
        runtimeOverrides = CredentialOfferRuntimeOverrides(
            selectiveDisclosure = SDMap.generateSDMap(
                listOf(
                    "credentialSubject.givenName",
                    "credentialSubject.familyName",
                )
            ),
        ),
    )

    val PROFILE_AUTHORIZED_OFFER = CredentialOfferCreateRequest(
        profileId = W3C_PROFILE_ID,
        authMethod = AuthenticationMethod.AUTHORIZED,
        issuerStateMode = IssuerStateMode.OMIT,
    )

    val PROFILE_AUTHORIZED_OFFER_BY_REFERENCE = PROFILE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
    )

    val PROFILE_AUTHORIZED_OFFER_BY_VALUE = PROFILE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_VALUE,
    )

    val PROFILE_AUTHORIZED_OFFER_BY_VALUE_AND_ISSUER_STATE = PROFILE_AUTHORIZED_OFFER.copy(
        issuerStateMode = IssuerStateMode.INCLUDE,
        valueMode = CredentialOfferValueMode.BY_VALUE,
    )

    val PRE_AUTHORIZED_MDOC_PHOTO_ID_OFFER = CredentialOfferCreateRequest(
        profileId = MDOC_PHOTO_ID_PROFILE_ID,
        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
        issuerStateMode = IssuerStateMode.OMIT,
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
        runtimeOverrides = CredentialOfferRuntimeOverrides(
            credentialData = buildJsonObject {
                putJsonObject("org.iso.23220.photoid.1") {
                    put("age_over_18", true)
                    put("issuing_country", "AT")
                    put("given_name_unicode", "Jane")
                    put("family_name_unicode", "Doe")
                    put("birth_date", "2003-12-21")
                    put("issuance_date", "2025-12-13")
                    put("issuing_authority_unicode", "Walt.id Issuer")
                    put("expiry_date", "2026-12-13")
                    put("portrait", "AQIDBAUGBwgJCgsMDQ4P")
                }
            },
        ),
    )

    val AUTHORIZED_MDOC_MDL_OFFER = CredentialOfferCreateRequest(
        profileId = MDOC_MDL_PROFILE_ID,
        authMethod = AuthenticationMethod.AUTHORIZED,
        issuerStateMode = IssuerStateMode.INCLUDE,
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
        runtimeOverrides = CredentialOfferRuntimeOverrides(
            credentialData = buildJsonObject {
                putJsonObject("org.iso.18013.5.1") {
                    put("family_name", "Doe")
                    put("given_name", "Jane")
                    put("birth_date", "1986-03-22")
                    put("issue_date", "2019-10-20")
                    put("expiry_date", "2024-10-20")
                    put("issuing_country", "AT")
                    put("issuing_authority", "AT DMV")
                    put("document_number", "123456789")
                    put("portrait", "AQIDBAUGBwgJCgsMDQ4P")
                    putJsonArray("driving_privileges") {
                        addJsonObject {
                            put("vehicle_category_code", "A")
                            put("issue_date", "2018-08-09")
                            put("expiry_date", "2024-10-20")
                        }
                        addJsonObject {
                            put("vehicle_category_code", "B")
                            put("issue_date", "2017-02-23")
                            put("expiry_date", "2024-10-20")
                        }
                    }
                    put("un_distinguishing_sign", "AT")
                }
            },
        ),
    )

    val CREDENTIAL_OFFER_RESPONSE_BY_REFERENCE = CredentialOfferCreateResponse(
        offerId = EXAMPLE_OFFER_ID,
        profileId = W3C_PROFILE_ID,
        authMethod = AuthenticationMethod.AUTHORIZED,
        issuerStateMode = IssuerStateMode.OMIT,
        expiresAt = EXAMPLE_EXPIRES_AT,
        credentialOffer = byReferenceOfferUrl(),
    )

    val CREDENTIAL_OFFER_RESPONSE_BY_VALUE = CredentialOfferCreateResponse(
        offerId = EXAMPLE_OFFER_ID,
        profileId = W3C_PROFILE_ID,
        authMethod = AuthenticationMethod.AUTHORIZED,
        issuerStateMode = IssuerStateMode.OMIT,
        expiresAt = EXAMPLE_EXPIRES_AT,
        credentialOffer = byValueAuthorizationOfferUrl(),
    )

    val CREDENTIAL_OFFER_RESPONSE_BY_VALUE_WITH_ISSUER_STATE = CredentialOfferCreateResponse(
        offerId = EXAMPLE_OFFER_ID,
        profileId = W3C_PROFILE_ID,
        authMethod = AuthenticationMethod.AUTHORIZED,
        issuerStateMode = IssuerStateMode.INCLUDE,
        expiresAt = EXAMPLE_EXPIRES_AT,
        credentialOffer = byValueAuthorizationOfferUrl(issuerState = EXAMPLE_OFFER_ID),
    )

    val PRE_AUTHORIZED_CREDENTIAL_OFFER_RESPONSE_WITH_GENERATED_TX_CODE = CredentialOfferCreateResponse(
        offerId = EXAMPLE_OFFER_ID,
        profileId = W3C_PROFILE_ID,
        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
        issuerStateMode = IssuerStateMode.OMIT,
        expiresAt = EXAMPLE_EXPIRES_AT,
        txCodeValue = "483921",
        credentialOffer = byReferenceOfferUrl(),
    )

    val PRE_AUTHORIZED_CREDENTIAL_OFFER_RESPONSE_WITH_PROVIDED_TX_CODE = CredentialOfferCreateResponse(
        offerId = EXAMPLE_OFFER_ID,
        profileId = W3C_PROFILE_ID,
        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
        issuerStateMode = IssuerStateMode.OMIT,
        expiresAt = EXAMPLE_EXPIRES_AT,
        txCodeValue = PROVIDED_TX_CODE_VALUE,
        credentialOffer = byReferenceOfferUrl(),
    )

    val CREDENTIAL_OFFER_RESPONSE = PRE_AUTHORIZED_CREDENTIAL_OFFER_RESPONSE_WITH_PROVIDED_TX_CODE
    val PRE_AUTHORIZED_CREDENTIAL_OFFER = PROFILE_PRE_AUTHORIZED_OFFER_WITH_PROVIDED_TX_CODE
    val AUTHORIZED_CREDENTIAL_OFFER = PROFILE_AUTHORIZED_OFFER_BY_REFERENCE

    private fun byReferenceOfferUrl(): String =
        CredentialOfferRequest(
            credentialOfferUri = "$EXAMPLE_CREDENTIAL_ISSUER/credential-offer?id=$EXAMPLE_OFFER_ID",
        ).toUrl()

    private fun byValueAuthorizationOfferUrl(issuerState: String? = null): String =
        CredentialOfferRequest(
            credentialOffer = CredentialOffer.withAuthorizationCodeGrant(
                credentialIssuer = EXAMPLE_CREDENTIAL_ISSUER,
                credentialConfigurationIds = listOf(W3C_CREDENTIAL_CONFIGURATION_ID),
                issuerState = issuerState,
            )
        ).toUrl()

    private const val W3C_PROFILE_ID = "universityDegree"
    private const val MDOC_PHOTO_ID_PROFILE_ID = "isoPhotoId"
    private const val MDOC_MDL_PROFILE_ID = "isoMdl"
    private const val W3C_CREDENTIAL_CONFIGURATION_ID = "UniversityDegree_jwt_vc_json"
    private const val EXAMPLE_CREDENTIAL_ISSUER = "http://localhost:7003/openid4vci"
    private const val EXAMPLE_OFFER_ID = "018f8d6e-8df4-7b73-9f3d-f3df21a4374a"
    private const val EXAMPLE_EXPIRES_AT = 1_739_000_000_000
    private const val PROVIDED_TX_CODE_VALUE = "123456"
}
