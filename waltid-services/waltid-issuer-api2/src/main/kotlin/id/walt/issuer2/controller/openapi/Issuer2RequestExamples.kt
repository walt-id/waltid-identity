package id.walt.issuer2.controller.openapi

import id.walt.issuer2.models.CredentialOfferCreateRequest
import id.walt.issuer2.models.CredentialOfferCreateResponse
import id.walt.issuer2.models.CredentialOfferCredential
import id.walt.issuer2.models.CredentialOfferRequestBody
import id.walt.issuer2.models.MultiCredentialOfferCreateRequest
import id.walt.issuer2.models.MultiCredentialOfferCreateResponse
import id.walt.issuer2.models.CredentialOfferRuntimeOverrides
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.CredentialOfferRequest
import id.walt.openid4vci.offers.CredentialOfferValueMode
import id.walt.openid4vci.offers.IssuerStateMode
import id.walt.openid4vci.offers.TxCode
import id.walt.openid4vci.proofs.ProofType
import id.walt.sdjwt.SDMap
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
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

    private val EXAMPLE_ISSUER_KEY = buildJsonObject {
        put("type", "jwk")
        putJsonObject("jwk") {
            put("kty", "EC")
            put("d", "KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ")
            put("crv", "P-256")
            put("x", "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM")
            put("y", "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E")
        }
    }

    val PROFILE_PRE_AUTHORIZED_OFFER = CredentialOfferCreateRequest(
        profileId = W3C_PROFILE_ID,
        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE = PROFILE_PRE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_BY_VALUE = PROFILE_PRE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_VALUE,
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_W3C_STATUS = PROFILE_PRE_AUTHORIZED_OFFER.copy(
        runtimeOverrides = CredentialOfferRuntimeOverrides(
            credentialStatus = buildJsonObject {
                put("id", "https://status.example.com/list/1#94567")
                put("type", "BitstringStatusListEntry")
                put("statusPurpose", "revocation")
                put("statusListIndex", "94567")
                put("statusListCredential", "https://status.example.com/list/1")
            },
        ),
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_SD_JWT_STATUS = PROFILE_PRE_AUTHORIZED_OFFER.copy(
        profileId = IDENTITY_SD_JWT_PROFILE_ID,
        runtimeOverrides = CredentialOfferRuntimeOverrides(credentialStatus = tokenStatusEntry(94567)),
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_MDOC_STATUS = PROFILE_PRE_AUTHORIZED_OFFER.copy(
        profileId = MDOC_MDL_PROFILE_ID,
        runtimeOverrides = CredentialOfferRuntimeOverrides(credentialStatus = tokenStatusEntry(94567)),
    )

    val PROFILE_PRE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_DISTINCT_STATUSES = MultiCredentialOfferCreateRequest(
        credentials = listOf(94567, 12345).map { index ->
            CredentialOfferCredential(
                profileId = IDENTITY_SD_JWT_PROFILE_ID,
                runtimeOverrides = CredentialOfferRuntimeOverrides(credentialStatus = tokenStatusEntry(index)),
            )
        },
        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
    )

    val PROFILE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_DISTINCT_STATUSES =
        PROFILE_PRE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_DISTINCT_STATUSES.copy(
            authMethod = AuthenticationMethod.AUTHORIZED,
            issuerStateMode = IssuerStateMode.INCLUDE,
        )

    private fun tokenStatusEntry(index: Int) = buildJsonObject {
        putJsonObject("status_list") {
            put("idx", index)
            put("uri", "https://status.example.com/list/1")
        }
    }

    val PROFILE_PRE_AUTHORIZED_MULTI_CREDENTIAL_OFFER = MultiCredentialOfferCreateRequest(
        credentials = listOf(
            CredentialOfferCredential(W3C_PROFILE_ID),
            CredentialOfferCredential(MDOC_PHOTO_ID_PROFILE_ID),
        ),
        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
    )

    val PROFILE_PRE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_BY_VALUE =
        PROFILE_PRE_AUTHORIZED_MULTI_CREDENTIAL_OFFER.copy(
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
        profileId = W3C_PROFILE_ID,
        runtimeOverrides = CredentialOfferRuntimeOverrides(
            credentialData = buildJsonObject {
                putJsonObject("credentialSubject") {
                    putJsonObject("achievement") {
                        put("name", "Computer Science")
                    }
                }
            },
        ),
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_WITH_ISSUER_KEY_OVERRIDE = PROFILE_PRE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
        profileId = W3C_PROFILE_ID,
        runtimeOverrides = CredentialOfferRuntimeOverrides(
            issuerKey = EXAMPLE_ISSUER_KEY,
        ),
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_WITH_SELECTIVE_DISCLOSURE_OVERRIDE = PROFILE_PRE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
        profileId = W3C_PROFILE_ID,
        runtimeOverrides = CredentialOfferRuntimeOverrides(
            selectiveDisclosure = SDMap.generateSDMap(
                listOf(
                    "credentialSubject.achievement.type",
                    "credentialSubject.achievement.name",
                )
            ),
        ),
    )

    val PROFILE_AUTHORIZED_OFFER = CredentialOfferCreateRequest(
        profileId = W3C_PROFILE_ID,
        authMethod = AuthenticationMethod.AUTHORIZED,
    )

    val PROFILE_AUTHORIZED_OFFER_BY_REFERENCE = PROFILE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
    )

    val PROFILE_AUTHORIZED_OFFER_BY_VALUE = PROFILE_AUTHORIZED_OFFER.copy(
        valueMode = CredentialOfferValueMode.BY_VALUE,
    )

    val PROFILE_AUTHORIZED_OFFER_BY_VALUE_WITHOUT_ISSUER_STATE = PROFILE_AUTHORIZED_OFFER.copy(
        issuerStateMode = IssuerStateMode.OMIT,
        valueMode = CredentialOfferValueMode.BY_VALUE,
    )

    val PROFILE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_BY_REFERENCE = MultiCredentialOfferCreateRequest(
        credentials = listOf(
            CredentialOfferCredential(EUDI_PID_SD_JWT_PROFILE_ID),
            CredentialOfferCredential(EUDI_PID_MDOC_PROFILE_ID),
        ),
        authMethod = AuthenticationMethod.AUTHORIZED,
        issuerStateMode = IssuerStateMode.INCLUDE,
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
    )

    val PROFILE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_BY_VALUE = MultiCredentialOfferCreateRequest(
        credentials = listOf(
            CredentialOfferCredential(IDENTITY_SD_JWT_PROFILE_ID),
            CredentialOfferCredential(CERTIFICATE_OF_RESIDENCE_SD_JWT_PROFILE_ID),
        ),
        authMethod = AuthenticationMethod.AUTHORIZED,
        issuerStateMode = IssuerStateMode.INCLUDE,
        valueMode = CredentialOfferValueMode.BY_VALUE,
    )

    val PROFILE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_RUNTIME_OVERRIDES = MultiCredentialOfferCreateRequest(
        credentials = listOf(
            CredentialOfferCredential(
                profileId = W3C_PROFILE_ID,
                runtimeOverrides = CredentialOfferRuntimeOverrides(
                    credentialData = buildJsonObject {
                        putJsonObject("credentialSubject") {
                            putJsonObject("achievement") {
                                put("name", "OpenID4VCI Implementation")
                                put("description", "Implemented an OpenID4VCI issuer with batch issuance")
                                putJsonObject("criteria") {
                                    put("narrative", "The holder implemented and tested an interoperable issuer.")
                                }
                            }
                        }
                    },
                ),
            ),
            CredentialOfferCredential(
                profileId = IDENTITY_SD_JWT_PROFILE_ID,
                runtimeOverrides = CredentialOfferRuntimeOverrides(
                    credentialData = buildJsonObject {
                        put("given_name", "Alice")
                        put("family_name", "Doe")
                        put("birthdate", "1990-01-01")
                    },
                    selectiveDisclosure = SDMap.generateSDMap(
                        listOf("family_name", "birthdate"),
                    ),
                ),
            ),
        ),
        authMethod = AuthenticationMethod.AUTHORIZED,
        issuerStateMode = IssuerStateMode.INCLUDE,
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
    )

    val PROFILE_PRE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_RUNTIME_OVERRIDES =
        PROFILE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_RUNTIME_OVERRIDES.copy(
            authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            issuerStateMode = null,
        )

    val PRE_AUTHORIZED_MDOC_PHOTO_ID_OFFER_WITH_CREDENTIAL_DATA_OVERRIDE = CredentialOfferCreateRequest(
        profileId = MDOC_PHOTO_ID_PROFILE_ID,
        runtimeOverrides = CredentialOfferRuntimeOverrides(
            credentialData = buildJsonObject {
                putJsonObject("org.iso.23220.1") {
                    put("age_over_18", true)
                    put("issuing_country", "AT")
                    put("given_name", "Jane")
                    put("family_name", "Doe")
                    put("birth_date", "2003-12-21")
                    put("issue_date", "2025-12-13")
                    put("issuing_authority_unicode", "walt.id Issuer")
                    put("expiry_date", "2026-12-13")
                }
                putJsonObject("org.iso.23220.photoid.1") {
                    put("person_id", "123456")
                    put("administrative_number", "654321")
                }
            },
        ),
        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
    )

    val PROFILE_PRE_AUTHORIZED_OFFER_WITH_AUTHORIZED_TRANSACTION_DATA_TYPES_OVERRIDE =
        CredentialOfferCreateRequest(
            profileId = EU_AGE_VERIFICATION_PROFILE_ID,
            runtimeOverrides = CredentialOfferRuntimeOverrides(
                authorizedTransactionDataTypes = listOf(SCA_PAYMENT_TRANSACTION_DATA_TYPE),
            ),
            authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            valueMode = CredentialOfferValueMode.BY_REFERENCE,
        )

    val AUTHORIZED_MDOC_MDL_OFFER_WITH_CREDENTIAL_DATA_OVERRIDE = CredentialOfferCreateRequest(
        profileId = MDOC_MDL_PROFILE_ID,
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
        authMethod = AuthenticationMethod.AUTHORIZED,
        issuerStateMode = IssuerStateMode.INCLUDE,
        valueMode = CredentialOfferValueMode.BY_REFERENCE,
    )

    val CREDENTIAL_OFFER_RESPONSE_BY_REFERENCE = CredentialOfferCreateResponse(
        offerId = EXAMPLE_OFFER_ID,
        profileId = W3C_PROFILE_ID,
        authMethod = AuthenticationMethod.AUTHORIZED,
        issuerStateMode = IssuerStateMode.INCLUDE,
        expiresAt = EXAMPLE_EXPIRES_AT,
        credentialOffer = byReferenceOfferUrl(),
    )

    val CREDENTIAL_OFFER_RESPONSE_BY_VALUE = CredentialOfferCreateResponse(
        offerId = EXAMPLE_OFFER_ID,
        profileId = W3C_PROFILE_ID,
        authMethod = AuthenticationMethod.AUTHORIZED,
        issuerStateMode = IssuerStateMode.INCLUDE,
        expiresAt = EXAMPLE_EXPIRES_AT,
        credentialOffer = byValueAuthorizationOfferUrl(),
    )

    val CREDENTIAL_OFFER_RESPONSE_BY_VALUE_WITH_ISSUER_STATE = CredentialOfferCreateResponse(
        offerId = EXAMPLE_OFFER_ID,
        profileId = W3C_PROFILE_ID,
        authMethod = AuthenticationMethod.AUTHORIZED,
        issuerStateMode = IssuerStateMode.INCLUDE,
        expiresAt = EXAMPLE_EXPIRES_AT,
        credentialOffer = byValueAuthorizationOfferUrl(),
    )

    val MULTI_CREDENTIAL_OFFER_RESPONSE_BY_REFERENCE = MultiCredentialOfferCreateResponse(
        offerId = EXAMPLE_OFFER_ID,
        authMethod = AuthenticationMethod.AUTHORIZED,
        issuerStateMode = IssuerStateMode.INCLUDE,
        expiresAt = EXAMPLE_EXPIRES_AT,
        credentialOffer = byReferenceOfferUrl(),
    )

    val MULTI_CREDENTIAL_OFFER_RESPONSE_BY_VALUE = MultiCredentialOfferCreateResponse(
        offerId = EXAMPLE_OFFER_ID,
        authMethod = AuthenticationMethod.AUTHORIZED,
        issuerStateMode = IssuerStateMode.INCLUDE,
        expiresAt = EXAMPLE_EXPIRES_AT,
        credentialOffer = byValueMultiDatasetAuthorizationOfferUrl(),
    )

    val PRE_AUTHORIZED_CREDENTIAL_OFFER_RESPONSE_WITH_GENERATED_TX_CODE = CredentialOfferCreateResponse(
        offerId = EXAMPLE_OFFER_ID,
        profileId = W3C_PROFILE_ID,
        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
        expiresAt = EXAMPLE_EXPIRES_AT,
        txCodeValue = "483921",
        credentialOffer = byReferenceOfferUrl(),
    )

    val PRE_AUTHORIZED_CREDENTIAL_OFFER_RESPONSE_WITH_PROVIDED_TX_CODE = CredentialOfferCreateResponse(
        offerId = EXAMPLE_OFFER_ID,
        profileId = W3C_PROFILE_ID,
        authMethod = AuthenticationMethod.PRE_AUTHORIZED,
        expiresAt = EXAMPLE_EXPIRES_AT,
        txCodeValue = PROVIDED_TX_CODE_VALUE,
        credentialOffer = byReferenceOfferUrl(),
    )

    val CREDENTIAL_OFFER_RESPONSE = PRE_AUTHORIZED_CREDENTIAL_OFFER_RESPONSE_WITH_PROVIDED_TX_CODE
    val PRE_AUTHORIZED_CREDENTIAL_OFFER = PROFILE_PRE_AUTHORIZED_OFFER_WITH_PROVIDED_TX_CODE
    val AUTHORIZED_CREDENTIAL_OFFER = PROFILE_AUTHORIZED_OFFER_BY_REFERENCE

    // Decodable documentation fixtures only: the certificate, nonce, times and signatures must be replaced.
    private val EXAMPLE_ATTESTED_HOLDER_JWK = buildJsonObject {
        put("kty", "EC")
        put("crv", "P-256")
        put("x", "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc")
        put("y", "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ")
    }

    private val EXAMPLE_SECOND_ATTESTED_HOLDER_JWK = buildJsonObject {
        put("kty", "EC")
        put("crv", "P-256")
        put("x", "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM")
        put("y", "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E")
    }

    private val EXAMPLE_KEY_ATTESTATION = illustrativeKeyAttestation(
        listOf(EXAMPLE_ATTESTED_HOLDER_JWK, EXAMPLE_SECOND_ATTESTED_HOLDER_JWK),
    )

    private val EXAMPLE_DID_ATTESTED_KEYS = listOf(EXAMPLE_ATTESTED_HOLDER_JWK, EXAMPLE_SECOND_ATTESTED_HOLDER_JWK).map { jwk ->
        // Encode the public key without kid, then attach its DID verification method reference.
        val did = "did:jwk:" + Base64.UrlSafe.encode(jwk.toString().encodeToByteArray()).trimEnd('=')
        JsonObject(jwk + ("kid" to JsonPrimitive("$did#0")))
    }

    private val EXAMPLE_DID_KEY_ATTESTATION = illustrativeKeyAttestation(EXAMPLE_DID_ATTESTED_KEYS)

    private fun illustrativeKeyAttestation(keys: List<JsonObject>) = illustrativeJwt(
        header = buildJsonObject {
            put("alg", "ES256")
            put("typ", "key-attestation+jwt")
            putJsonArray("x5c") { add("base64-DER-attester-signing-certificate") }
        },
        payload = buildJsonObject {
            put("iss", "https://wallet-provider.example")
            put("iat", 1_800_000_000)
            put("exp", 1_800_000_300)
            put("nonce", "c_nonce-from-issuer-nonce-endpoint")
            put("attested_keys", JsonArray(keys))
        },
    )

    private val EXAMPLE_JWT_WITH_KEY_ATTESTATION = illustrativeJwt(
        header = buildJsonObject {
            put("alg", "ES256")
            put("typ", "openid4vci-proof+jwt")
            put("jwk", EXAMPLE_ATTESTED_HOLDER_JWK)
            put("key_attestation", EXAMPLE_KEY_ATTESTATION)
        },
        payload = buildJsonObject {
            put("aud", EXAMPLE_CREDENTIAL_ISSUER)
            put("iat", 1_800_000_000)
            put("nonce", "c_nonce-from-issuer-nonce-endpoint")
        },
    )

    val SD_JWT_CREDENTIAL_REQUEST_WITH_KEY_ATTESTATION = credentialProofExample(
        IDENTITY_SD_JWT_CONFIGURATION_ID, ProofType.JWT, EXAMPLE_JWT_WITH_KEY_ATTESTATION,
    )

    val MDOC_CREDENTIAL_REQUEST_WITH_KEY_ATTESTATION = credentialProofExample(
        MDOC_CREDENTIAL_CONFIGURATION_ID, ProofType.JWT, EXAMPLE_JWT_WITH_KEY_ATTESTATION,
    )

    val SD_JWT_CREDENTIAL_REQUEST_WITH_ATTESTATION_PROOF = credentialProofExample(
        IDENTITY_SD_JWT_CONFIGURATION_ID, ProofType.ATTESTATION, EXAMPLE_KEY_ATTESTATION,
    )

    val MDOC_CREDENTIAL_REQUEST_WITH_ATTESTATION_PROOF = credentialProofExample(
        MDOC_CREDENTIAL_CONFIGURATION_ID, ProofType.ATTESTATION, EXAMPLE_KEY_ATTESTATION,
    )

    val W3C_CREDENTIAL_REQUEST_WITH_KEY_ATTESTATION = credentialProofExample(
        W3C_CREDENTIAL_CONFIGURATION_ID, ProofType.JWT, illustrativeJwt(
            header = buildJsonObject {
                put("alg", "ES256")
                put("typ", "openid4vci-proof+jwt")
                put("kid", EXAMPLE_DID_ATTESTED_KEYS.first().getValue("kid"))
                put("key_attestation", EXAMPLE_DID_KEY_ATTESTATION)
            },
            payload = buildJsonObject {
                put("aud", EXAMPLE_CREDENTIAL_ISSUER)
                put("iat", 1_800_000_000)
                put("nonce", "c_nonce-from-issuer-nonce-endpoint")
            },
        ),
    )

    val W3C_CREDENTIAL_REQUEST_WITH_ATTESTATION_PROOF = credentialProofExample(
        W3C_CREDENTIAL_CONFIGURATION_ID, ProofType.ATTESTATION, EXAMPLE_DID_KEY_ATTESTATION,
    )

    private fun credentialProofExample(configurationId: String, proofType: ProofType, jwt: String) = buildJsonObject {
        put("credential_configuration_id", configurationId)
        putJsonObject("proofs") {
            putJsonArray(proofType.value) { add(jwt) }
        }
    }

    private fun illustrativeJwt(header: JsonObject, payload: JsonObject): String =
        listOf(header.toString(), payload.toString(), "illustrative-signature-not-valid")
            .joinToString(".") { Base64.UrlSafe.encode(it.encodeToByteArray()).trimEnd('=') }

    val BATCH_CREDENTIAL_REQUEST_BY_CONFIGURATION_ID = buildJsonObject {
        put("credential_configuration_id", W3C_CREDENTIAL_CONFIGURATION_ID)
        putJsonObject("proofs") {
            putJsonArray(ProofType.JWT.value) {
                add(EXAMPLE_PROOF_JWT_1)
                add(EXAMPLE_PROOF_JWT_2)
            }
        }
    }

    val BATCH_CREDENTIAL_REQUEST_BY_CREDENTIAL_IDENTIFIER = buildJsonObject {
        put("credential_identifier", EXAMPLE_CREDENTIAL_IDENTIFIER)
        putJsonObject("proofs") {
            putJsonArray(ProofType.JWT.value) {
                add(EXAMPLE_PROOF_JWT_1)
                add(EXAMPLE_PROOF_JWT_2)
            }
        }
    }

    val BATCH_CREDENTIAL_RESPONSE = buildJsonObject {
        putJsonArray("credentials") {
            addJsonObject {
                put("credential", EXAMPLE_ISSUED_CREDENTIAL_1)
            }
            addJsonObject {
                put("credential", EXAMPLE_ISSUED_CREDENTIAL_2)
            }
        }
    }

    fun credentialOfferRequestExamples(): List<Pair<String, CredentialOfferRequestBody>> = listOf(
        "[authorized][single][by-reference]" to PROFILE_AUTHORIZED_OFFER_BY_REFERENCE,
        "[authorized][single][by-value]" to PROFILE_AUTHORIZED_OFFER_BY_VALUE,
        "[authorized][single][by-value][issuer_state omitted]" to PROFILE_AUTHORIZED_OFFER_BY_VALUE_WITHOUT_ISSUER_STATE,
        "[authorized][single][by-reference][mDoc credentialData override]" to AUTHORIZED_MDOC_MDL_OFFER_WITH_CREDENTIAL_DATA_OVERRIDE,
        "[authorized][multiple][by-reference][same dataset, different formats][EUDI PID]" to PROFILE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_BY_REFERENCE,
        "[authorized][multiple][by-value][same format, different datasets][SD-JWT VC]" to PROFILE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_BY_VALUE,
        "[authorized][multiple][by-reference][runtime overrides]" to PROFILE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_RUNTIME_OVERRIDES,
        "[pre-authorized][single][by-reference]" to PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE,
        "[pre-authorized][single][shared status][W3C]" to PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_W3C_STATUS,
        "[pre-authorized][single][shared status][SD-JWT]" to PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_SD_JWT_STATUS,
        "[pre-authorized][single][shared status][mdoc]" to PROFILE_PRE_AUTHORIZED_OFFER_WITH_SHARED_MDOC_STATUS,
        "[pre-authorized][multiple][different statuses per item]" to PROFILE_PRE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_DISTINCT_STATUSES,
        "[authorized][multiple][different statuses per item]" to PROFILE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_DISTINCT_STATUSES,
        "[pre-authorized][single][by-value]" to PROFILE_PRE_AUTHORIZED_OFFER_BY_VALUE,
        "[pre-authorized][single][by-reference][provided tx_code]" to PROFILE_PRE_AUTHORIZED_OFFER_WITH_PROVIDED_TX_CODE,
        "[pre-authorized][single][by-reference][generated tx_code]" to PROFILE_PRE_AUTHORIZED_OFFER_WITH_GENERATED_TX_CODE,
        "[pre-authorized][single][by-reference][expires in 2 minutes]" to PROFILE_PRE_AUTHORIZED_OFFER_WITH_2_MIN_EXPIRY,
        "[pre-authorized][single][by-reference][no expiry]" to PROFILE_PRE_AUTHORIZED_OFFER_WITHOUT_EXPIRY,
        "[pre-authorized][single][by-reference][credentialData override]" to PROFILE_PRE_AUTHORIZED_OFFER_WITH_CREDENTIAL_DATA_OVERRIDE,
        "[pre-authorized][single][by-reference][issuerKey override]" to PROFILE_PRE_AUTHORIZED_OFFER_WITH_ISSUER_KEY_OVERRIDE,
        "[pre-authorized][single][by-reference][selective disclosure override]" to PROFILE_PRE_AUTHORIZED_OFFER_WITH_SELECTIVE_DISCLOSURE_OVERRIDE,
        "[pre-authorized][single][by-reference][mDoc credentialData override]" to PRE_AUTHORIZED_MDOC_PHOTO_ID_OFFER_WITH_CREDENTIAL_DATA_OVERRIDE,
        "[pre-authorized][single][by-reference][authorized transaction types override]" to PROFILE_PRE_AUTHORIZED_OFFER_WITH_AUTHORIZED_TRANSACTION_DATA_TYPES_OVERRIDE,
        "[pre-authorized][multiple][by-reference]" to PROFILE_PRE_AUTHORIZED_MULTI_CREDENTIAL_OFFER,
        "[pre-authorized][multiple][by-value]" to PROFILE_PRE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_BY_VALUE,
        "[pre-authorized][multiple][by-reference][runtime overrides]" to PROFILE_PRE_AUTHORIZED_MULTI_CREDENTIAL_OFFER_WITH_RUNTIME_OVERRIDES,
    )

    private fun byReferenceOfferUrl(): String =
        CredentialOfferRequest(
            credentialOfferUri = "$EXAMPLE_CREDENTIAL_ISSUER/credential-offer?id=$EXAMPLE_OFFER_ID",
        ).toUrl()

    private fun byValueAuthorizationOfferUrl(issuerState: String? = EXAMPLE_OFFER_ID): String =
        CredentialOfferRequest(
            credentialOffer = CredentialOffer.withAuthorizationCodeGrant(
                credentialIssuer = EXAMPLE_CREDENTIAL_ISSUER,
                credentialConfigurationIds = listOf(W3C_CREDENTIAL_CONFIGURATION_ID),
                issuerState = issuerState,
            )
        ).toUrl()

    private fun byValueMultiDatasetAuthorizationOfferUrl(): String =
        CredentialOfferRequest(
            credentialOffer = CredentialOffer.withAuthorizationCodeGrant(
                credentialIssuer = EXAMPLE_CREDENTIAL_ISSUER,
                credentialConfigurationIds = listOf(
                    IDENTITY_SD_JWT_CONFIGURATION_ID,
                    CERTIFICATE_OF_RESIDENCE_SD_JWT_CONFIGURATION_ID,
                ),
                issuerState = EXAMPLE_OFFER_ID,
            )
        ).toUrl()

    private const val W3C_PROFILE_ID = "openBadgeCredential"
    private const val MDOC_PHOTO_ID_PROFILE_ID = "isoPhotoId"
    private const val MDOC_MDL_PROFILE_ID = "isoMdl"
    private const val IDENTITY_SD_JWT_PROFILE_ID = "identityCredentialSdJwt"
    private const val CERTIFICATE_OF_RESIDENCE_SD_JWT_PROFILE_ID = "certificateOfResidenceSdJwt"
    private const val EUDI_PID_SD_JWT_PROFILE_ID = "eudiPidSdJwt"
    private const val EUDI_PID_MDOC_PROFILE_ID = "eudiPidMdoc"
    private const val EU_AGE_VERIFICATION_PROFILE_ID = "euAgeVerificationMdoc"
    private const val SCA_PAYMENT_TRANSACTION_DATA_TYPE = "urn:eudi:sca:payment:1"
    private const val W3C_CREDENTIAL_CONFIGURATION_ID = "OpenBadgeCredential_jwt_vc_json"
    private const val EUDI_PID_SD_JWT_CONFIGURATION_ID = "urn:eudi:pid:1"
    private const val EUDI_PID_MDOC_CONFIGURATION_ID = "eu.europa.ec.eudi.pid.1"
    private const val IDENTITY_SD_JWT_CONFIGURATION_ID = "identity_credential"
    private const val CERTIFICATE_OF_RESIDENCE_SD_JWT_CONFIGURATION_ID = "urn:eu.europa.ec.eudi:cor:1"
    private const val MDOC_CREDENTIAL_CONFIGURATION_ID = "org.iso.23220.photoid.1"
    private const val EXAMPLE_CREDENTIAL_ISSUER = "http://localhost:7002/openid4vci"
    private const val EXAMPLE_OFFER_ID = "018f8d6e-8df4-7b73-9f3d-f3df21a4374a"
    private const val EXAMPLE_EXPIRES_AT = 1_739_000_000_000
    private const val PROVIDED_TX_CODE_VALUE = "123456"
    private const val EXAMPLE_CREDENTIAL_IDENTIFIER = "credential-identifier-from-token-response"
    private const val EXAMPLE_PROOF_JWT_1 =
        "eyJhbGciOiJFUzI1NiIsInR5cCI6Im9wZW5pZDR2Y2ktcHJvb2Yrand0In0.proof-payload-1.proof-signature-1"
    private const val EXAMPLE_PROOF_JWT_2 =
        "eyJhbGciOiJFUzI1NiIsInR5cCI6Im9wZW5pZDR2Y2ktcHJvb2Yrand0In0.proof-payload-2.proof-signature-2"
    private const val EXAMPLE_ISSUED_CREDENTIAL_1 = "eyJhbGciOiJFUzI1NiJ9.credential-payload-1.credential-signature-1"
    private const val EXAMPLE_ISSUED_CREDENTIAL_2 = "eyJhbGciOiJFUzI1NiJ9.credential-payload-2.credential-signature-2"
}
