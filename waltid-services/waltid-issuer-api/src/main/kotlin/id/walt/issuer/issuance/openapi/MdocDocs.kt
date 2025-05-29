package id.walt.issuer.issuance.openapi

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.sessionTtlHeader
import id.walt.issuer.issuance.statusCallbackUriHeader
import io.github.smiley4.ktoropenapi.config.RequestConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray

object MdocDocs {

    private val mdlBaseIssuanceExample = Json.decodeFromString<IssuanceRequest>(
        """
        {
            "issuerKey": {
                "type": "jwk",
                "jwk": {
                    "kty": "EC",
                    "d": "KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ",
                    "crv": "P-256",
                    "x": "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM",
                    "y": "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"
                }
            },
            "credentialConfigurationId": "org.iso.18013.5.1.mDL",
            "mdocData": {
                "org.iso.18013.5.1": {
                    "family_name": "Doe",
                    "given_name": "John",
                    "birth_date": "1986-03-22",
                    "issue_date": "2019-10-20",
                    "expiry_date": "2024-10-20",
                    "issuing_country": "US",
                    "issuing_authority": "CA DMV",
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
                    "un_distinguishing_sign": "US"
                }
            },
            "x5Chain": [
                "-----BEGIN CERTIFICATE-----\nMIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYDVQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8OxMheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB\n-----END CERTIFICATE-----"
            ]
        }
    """.trimIndent()
    )

    fun requestConfig(): RequestConfig.() -> Unit = {

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
                        this["org.iso.18013.5.1"] = JsonObject(
                            (this["org.iso.18013.5.1"] as JsonObject).toMutableMap().apply {
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
                        this["org.iso.18013.5.1"] = JsonObject(
                            (this["org.iso.18013.5.1"] as JsonObject).toMutableMap().apply {
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
                        this["org.iso.18013.5.1"] = JsonObject(
                            (this["org.iso.18013.5.1"] as JsonObject).toMutableMap().apply {
                                put("age_over_18", true.toJsonElement())
                                put("age_over_24", true.toJsonElement())
                                put("age_over_60", false.toJsonElement())

                                put("administrative_number", "123456789".toJsonElement())
                                put("sex", 9.toJsonElement())
                                put("height", 180.toJsonElement())
                                put("weight", 100.toJsonElement())
                                put("eye_colour", "black".toJsonElement())
                                put("hair_colour", "black".toJsonElement())
                                put("birth_place", "Kentucky".toJsonElement())
                                put("resident_address", "Some Street 4".toJsonElement())
                                put("portrait_capture_date", "2018-08-09".toJsonElement())
                                put("age_in_years", 33.toJsonElement())
                                put("age_birth_year", 1986.toJsonElement())

                                put("issuing_jurisdiction", "US-CA".toJsonElement())
                                put("nationality", "US".toJsonElement())
                                put("resident_city", "New York".toJsonElement())
                                put("resident_state", "New York".toJsonElement())
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
}
