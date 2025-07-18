package id.walt.verifier.oidc.models.presentedcredentials

import id.walt.sdjwt.SDJwtVC
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object PresentedSdJwtVcViewModeFormatter {

    private fun createPresentedSdJwtVcSimpleViewMode(
        sdJwtVcStr: String,
    ) = SDJwtVC.parse(sdJwtVcStr).let { sdJwtVc ->
        PresentedSdJwtVcSimpleViewMode(
            vc = ParsedJwt(
                header = sdJwtVc.header,
                payload = sdJwtVc.fullPayload,
            ),
            keyBinding = sdJwtVc.keyBindingJwt?.let { kbJwt ->
                ParsedJwt(
                    header = kbJwt.header,
                    payload = kbJwt.fullPayload,
                )
            },
        )
    }

    private fun createPresentedSdJwtVcVerboseViewMode(
        sdJwtVcStr: String,
    ) = SDJwtVC.parse(sdJwtVcStr).let { sdJwtVc ->
        PresentedSdJwtVcVerboseViewMode(
            raw = sdJwtVcStr,
            vc = ParsedSdJwtVerbose(
                header = sdJwtVc.header,
                fullPayload = sdJwtVc.fullPayload,
                undisclosedPayload = sdJwtVc.undisclosedPayload,
                disclosures = sdJwtVc.digestedDisclosures.takeIf { it.isNotEmpty() }?.let { digestedDisclosuresMap ->
                    digestedDisclosuresMap.mapValues { entry ->
                        ParsedDisclosure(
                            disclosure = entry.value.disclosure,
                            salt = entry.value.salt,
                            key = entry.value.key,
                            value = entry.value.value,
                        )
                    }
                },
            ),
            keyBinding = sdJwtVc.keyBindingJwt?.let { kbJwt ->
                ParsedJwt(
                    header = kbJwt.header,
                    payload = kbJwt.fullPayload,
                )
            },
        )
    }

    fun fromSdJwtVcString(
        sdJwtVcStr: String,
        viewMode: PresentedCredentialsViewMode = PresentedCredentialsViewMode.simple,
    ) = when (viewMode) {
        PresentedCredentialsViewMode.simple -> {
            createPresentedSdJwtVcSimpleViewMode(sdJwtVcStr)
        }

        PresentedCredentialsViewMode.verbose -> {
            createPresentedSdJwtVcVerboseViewMode(sdJwtVcStr)
        }
    }
}

@Serializable
@SerialName("sd_jwt_vc_view_simple")
data class PresentedSdJwtVcSimpleViewMode(
    val vc: ParsedJwt,
    val keyBinding: ParsedJwt? = null,
) : PresentedCredentialView()

@Serializable
@SerialName("sd_jwt_vc_view_verbose")
data class PresentedSdJwtVcVerboseViewMode(
    val raw: String,
    val vc: ParsedSdJwtVerbose,
    val keyBinding: ParsedJwt? = null,
) : PresentedCredentialView()



