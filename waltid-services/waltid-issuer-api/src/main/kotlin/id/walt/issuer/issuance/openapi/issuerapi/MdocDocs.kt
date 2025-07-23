package id.walt.issuer.issuance.openapi.issuerapi

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.issuer.issuance.IssuanceRequest
import io.github.smiley4.ktoropenapi.config.RequestConfig
import io.github.smiley4.ktoropenapi.config.RouteConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray

object MdocDocs {
    fun getMdocsDocs(): RouteConfig.() -> Unit = {
        summary = "Signs a credential based on the ISO/IEC 18013-5 mDoc format and starts an OIDC credential exchange flow."
        description = "This endpoint issues an mDoc, and returns an issuance URL"

        request(requestConfig())
    }

    private fun requestConfig(): RequestConfig.() -> Unit = {

        statusCallbackUriHeader()
        sessionTtlHeader()

        body<IssuanceRequest> {

            description =
                "Pass the unsigned credential that you intend to issue as the body of the request."
            required = true

            example(
                name = "mDL example - Mandatory fields only",
            ) {
                value = mdlBaseIssuanceExample
            }

            example(
                name = "mDL example - Mandatory fields & Age attestation (single)",
            ) {
                value = mdlBaseIssuanceExample.copy(
                    mdocData = mdlBaseIssuanceExample.mdocData!!.toMutableMap().apply {
                        this[ISO_IEC_MDL_NAMESPACE_ID] = JsonObject(
                            (this[ISO_IEC_MDL_NAMESPACE_ID] as JsonObject).toMutableMap().apply {
                                put("age_over_18", true.toJsonElement())
                            }
                        )
                    }
                )
            }

            example(
                name = "mDL example - Mandatory fields & Age attestations (multiple)",
            ) {
                value = mdlBaseIssuanceExample.copy(
                    mdocData = mdlBaseIssuanceExample.mdocData!!.toMutableMap().apply {
                        this[ISO_IEC_MDL_NAMESPACE_ID] = JsonObject(
                            (this[ISO_IEC_MDL_NAMESPACE_ID] as JsonObject).toMutableMap().apply {
                                put("age_over_18", true.toJsonElement())
                                put("age_over_24", true.toJsonElement())
                                put("age_over_60", false.toJsonElement())
                            }
                        )
                    }
                )
            }

            example(
                name = "mDL example - All fields & Age attestations (multiple)",
            ) {
                value = mdlBaseIssuanceExample.copy(
                    mdocData = mdlBaseIssuanceExample.mdocData!!.toMutableMap().apply {
                        this[ISO_IEC_MDL_NAMESPACE_ID] = JsonObject(
                            (this[ISO_IEC_MDL_NAMESPACE_ID] as JsonObject).toMutableMap().apply {
                                put("age_over_18", true.toJsonElement())
                                put("age_over_24", true.toJsonElement())
                                put("age_over_60", false.toJsonElement())

                                put("administrative_number", "123456789".toJsonElement())
                                put("sex", 9.toJsonElement())
                                put("height", 180.toJsonElement())
                                put("weight", 100.toJsonElement())
                                put("eye_colour", "black".toJsonElement())
                                put("hair_colour", "black".toJsonElement())
                                put("birth_place", "Vienna".toJsonElement())
                                put("resident_address", "Some Street 4".toJsonElement())
                                put("portrait_capture_date", "2018-08-09".toJsonElement())
                                put("age_in_years", 33.toJsonElement())
                                put("age_birth_year", 1986.toJsonElement())

                                put("issuing_jurisdiction", "AT-9".toJsonElement())
                                put("nationality", "AT".toJsonElement())
                                put("resident_city", "Vienna".toJsonElement())
                                put("resident_state", "Vienna".toJsonElement())
                                put("resident_postal_code", "07008".toJsonElement())
                                put("biometric_template_face", listOf(141, 182, 121, 111, 238, 50, 120, 94, 54, 111, 113, 13, 241, 12, 12).toJsonElement())
                                put("family_name_national_character", "Doe".toJsonElement())
                                put("given_name_national_character", "John".toJsonElement())
                                put("signature_usual_mark", listOf(141, 182, 121, 111, 238, 50, 120, 94, 54, 111, 113, 13, 241, 12, 12).toJsonElement())
                            }
                        )
                    }
                )
            }

        }
    }

    private const val MDL_VC_CONFIG_ID = "org.iso.18013.5.1.mDL"
    private const val ISO_IEC_MDL_NAMESPACE_ID = "org.iso.18013.5.1"

    val mdlBaseIssuanceExample = Json.decodeFromString<IssuanceRequest>(
        """
        {
            "issuerKey": {
              "type": "jwk",
              "jwk": {
                "kty": "EC",
                "d": "-wSIL_tMH7-mO2NAfHn03I8ZWUHNXVzckTTb96Wsc1s",
                "crv": "P-256",
                "kid": "sW5yv0UmZ3S0dQuUrwlR9I3foREBHHFwXhGJGqGEVf0",
                "x": "Pzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5U",
                "y": "6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZo"
              }
            },
            "credentialConfigurationId": "$MDL_VC_CONFIG_ID",
            "mdocData": {
                "$ISO_IEC_MDL_NAMESPACE_ID": {
                    "family_name": "Doe",
                    "given_name": "John",
                    "birth_date": "1986-03-22",
                    "issue_date": "2019-10-20",
                    "expiry_date": "2024-10-20",
                    "issuing_country": "AT",
                    "issuing_authority": "AT DMV",
                    "document_number": "123456789",
                    "portrait": [141, 182, 121, 111, 238, 50, 120, 94, 54, 111, 113, 13, 241, 12, 12],
                    "driving_privileges": ${
            buildJsonArray {
                addJsonObject {
                    put("vehicle_category_code", "A".toJsonElement())
                    put("issue_date", "2018-08-09".toJsonElement())
                    put("expiry_date", "2024-10-20".toJsonElement())
                }
                addJsonObject {
                    put("vehicle_category_code", "B".toJsonElement())
                    put("issue_date", "2017-02-23".toJsonElement())
                    put("expiry_date", "2024-10-20".toJsonElement())
                }
            }
        },
                    "un_distinguishing_sign": "AT"
                }
            },
            "x5Chain": [
                "-----BEGIN CERTIFICATE-----\nMIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M\n-----END CERTIFICATE-----\n"
            ]
        }
    """.trimIndent()
    )

}
