package id.walt.verifier.oidc.models.presentedcredentials

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class PresentedCredentialView


@Serializable
@SerialName("jwt_vc_json_simple")
data class SimpleDecodedJwtVcJsonCredential(
    val placeHolder: String,
) : PresentedCredentialView() {

    companion object {

        fun fromJwtSecuredVpString(
            vp: String
        ): SimpleDecodedJwtVcJsonCredential {
            TODO("Not yet implemented")
        }

    }
}

@Serializable
@SerialName("jwt_vc_json_verbose")
data class VerboseDecodedJwtVcJsonCredential(
    val placeHolder: String,
) : PresentedCredentialView()


object DecodedJwtVcJsonCredentialFactory {

    fun fromJwtVpString(
        vp: String,
        viewMode: PresentedCredentialsViewMode = PresentedCredentialsViewMode.simple,
    ): PresentedCredentialView {
        TODO()
    }
}

@Serializable
@SerialName("sd_jwt_vc_simple")
data class SimpleDecodedSdJwtVcCredential(
    val placeHolder: String,
) : PresentedCredentialView()

@Serializable
@SerialName("sd_jwt_vc_verbose")
data class VerboseDecodedSdJwtVcCredential(
    val placeHolder: String,
) : PresentedCredentialView()

