package id.walt.verifier.oidc.models.presentedcredentials

import id.walt.w3c.utils.VCFormat
import kotlinx.serialization.Serializable

@Serializable
@ConsistentCopyVisibility
data class PresentationSessionPresentedCredentials private constructor(
    val credentialsByFormat: Map<VCFormat, List<PresentedCredentialView>>,
    val viewMode: PresentedCredentialsViewMode,
) {

    companion object {

        fun fromVpTokenStringsByFormat(
            vpTokenStringsByFormat: Map<VCFormat, List<String>>,
            viewMode: PresentedCredentialsViewMode,
        ) = PresentationSessionPresentedCredentials(
            credentialsByFormat = vpTokenStringsByFormat.mapValues { entry ->
                when (entry.key) {
                    VCFormat.sd_jwt_vc -> {
                        entry.value.map {
                            PresentedSdJwtVcViewModeFormatter.fromSdJwtVcString(
                                sdJwtVcStr = it,
                                viewMode = viewMode,
                            )
                        }
                    }

                    VCFormat.mso_mdoc -> {
                        entry.value.map {
                            PresentedMsoMdocViewModeFormatter.fromDeviceResponseString(
                                base64UrlEncodedDeviceResponse = it,
                                viewMode = viewMode,
                            )
                        }
                    }

                    VCFormat.jwt_vc_json -> {
                        entry.value.map {
                            PresentedJwtVcJsonViewModeFormatter.fromJwtVpString(
                                vpStr = it,
                                viewMode = viewMode,
                            )
                        }
                    }

                    else -> {
                        throw IllegalArgumentException("VC format ${entry.key} not supported")
                    }
                }
            },
            viewMode = viewMode,
        )
    }
}