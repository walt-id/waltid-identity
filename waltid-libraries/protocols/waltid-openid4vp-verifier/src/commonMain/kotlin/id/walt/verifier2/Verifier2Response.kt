package id.walt.verifier2

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Verifier2Response {

    @Serializable
    class Verifier2Error(
        val error: Verifier2ErrorType = Verifier2ErrorType.invalid_request,

        @SerialName("error_description")
        val errorDescription: String
    ) : Verifier2Response() {

        enum class Verifier2ErrorType {
            invalid_request,
            invalid_presentation
        }

        companion object {
            val MISSING_STATE_PARAMETER = Verifier2Error(errorDescription = "State parameter is missing.")
            val INVALID_STATE_PARAMETER = Verifier2Error(errorDescription = "Invalid or expired state.")
            val MALFORMED_VP_TOKEN = Verifier2Error(errorDescription = "Malformed vp_token.")

            val REQUIRED_CREDENTIALS_NOT_PROVIDED =
                Verifier2Error(errorDescription = "The presentation submission does not satisfy the requirements of the presentation definition. Required credentials were not provided.")

        }

        fun throwAsError(): Nothing = throw IllegalArgumentException("Verifier2 error: $error - $errorDescription")
    }

}
