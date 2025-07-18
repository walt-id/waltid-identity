package id.walt.verifier.oidc.models.presentedcredentials

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.sdjwt.SDJwtVC
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

object PresentedJwtVcJsonViewModeFormatter {

    private fun createPresentedJwtVcJsonSimpleViewMode(
        vpStr: String,
    ) = vpStr.decodeJws().let { vp ->
        PresentedJwtVcJsonSimpleViewMode(
            holder = (vp.payload["vp"] as JsonObject)["holder"],
            verifiableCredentials = ((vp.payload["vp"] as JsonObject)["verifiableCredential"] as JsonArray).map {
                SDJwtVC.parse(it.jsonPrimitive.content).let { sdJwtVc ->
                    ParsedJwt(
                        header = sdJwtVc.header,
                        payload = sdJwtVc.fullPayload,
                    )
                }
            },
        )
    }

    private fun createPresentedJwtVcJsonVerboseViewMode(
        vpStr: String,
    ) = vpStr.decodeJws().let { vp ->
        PresentedJwtVcJsonVerboseViewMode(
            vp = ParsedVerifiablePresentation(
                raw = vpStr,
                header = vp.header,
                payload = vp.payload,
            ),
            verifiableCredentials = ((vp.payload["vp"] as JsonObject)["verifiableCredential"] as JsonArray).map { vcStr ->
                SDJwtVC.parse(vcStr.jsonPrimitive.content).let { sdJwtVc ->
                    ParsedJwtVcJsonVerbose(
                        raw = vcStr.jsonPrimitive.content,
                        header = sdJwtVc.header,
                        fullPayload = sdJwtVc.fullPayload,
                        undisclosedPayload = sdJwtVc.undisclosedPayload,
                        disclosures = sdJwtVc.digestedDisclosures.takeIf { it.isNotEmpty() }
                            ?.let { digestedDisclosuresMap ->
                                digestedDisclosuresMap.mapValues { entry ->
                                    ParsedDisclosure(
                                        disclosure = entry.value.disclosure,
                                        salt = entry.value.salt,
                                        key = entry.value.key,
                                        value = entry.value.value,
                                    )
                                }
                            },
                    )

                }

            },
        )
    }

    fun fromJwtVpString(
        vpStr: String,
        viewMode: PresentedCredentialsViewMode = PresentedCredentialsViewMode.simple,
    ) = when (viewMode) {
        PresentedCredentialsViewMode.simple -> {
            createPresentedJwtVcJsonSimpleViewMode(vpStr)
        }

        PresentedCredentialsViewMode.verbose -> {
            createPresentedJwtVcJsonVerboseViewMode(vpStr)
        }
    }
}

@Serializable
@SerialName("jwt_vc_json_view_simple")
data class PresentedJwtVcJsonSimpleViewMode(
    val holder: JsonElement? = null,
    val verifiableCredentials: List<ParsedJwt>,
) : PresentedCredentialView()

@Serializable
@SerialName("jwt_vc_json_view_verbose")
data class PresentedJwtVcJsonVerboseViewMode(
    val vp: ParsedVerifiablePresentation,
    val verifiableCredentials: List<ParsedJwtVcJsonVerbose>,
) : PresentedCredentialView()

@Serializable
data class ParsedJwtVcJsonVerbose(
    val raw: String,
    val header: JsonObject,
    val fullPayload: JsonObject,
    val undisclosedPayload: JsonObject,
    val disclosures: Map<String, ParsedDisclosure>? = null,
)

@Serializable
data class ParsedVerifiablePresentation(
    val raw: String,
    val header: JsonObject,
    val payload: JsonObject,
)
