package id.walt.issuer.services.onboarding.models

import kotlinx.serialization.Serializable

@Serializable
data class DocumentSignerOnboardingRequest(
    val iacaSigner: IACASignerData,
    val certificateData: DocumentSignerCertificateData,
    val ecKeyGenRequestParams: KeyGenerationRequestParams = KeyGenerationRequestParams(
        backend = "jwk",
        config = null,
    ),
) {

    init {

        require( iacaSigner.certificateData.country == certificateData.country ) {
            "IACA and document signer country names must be the same"
        }

        require( iacaSigner.certificateData.stateOrProvinceName == certificateData.stateOrProvinceName ) {
            "IACA and document signer state/province names must be the same"
        }

        require( iacaSigner.certificateData.finalNotBefore <= certificateData.finalNotBefore ) {
            "IACA certificate not before must be before the document signer's not before"
        }

        require( iacaSigner.certificateData.finalNotAfter >= certificateData.finalNotAfter ) {
            "IACA certificate not after must be after the document signer's not after"
        }

    }

}