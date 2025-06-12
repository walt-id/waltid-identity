package id.walt.credentials.dcql

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.formats.DigitalCredential
import id.walt.dcql.DcqlCredential
import kotlinx.serialization.json.JsonObject

//object CredentialAdapter {
//
//    /**
//     * Represents a credential adapted from the custom DigitalCredential types.
//     */
//    private data class AdaptedCredential(
//        override val id: String,
//        override val format: String,
//        override val data: JsonObject,
//        //override val issuer: String?,
//    ) : DcqlCredential
//
//    /**
//     * Converts a list of custom credential representations into a list
//     * compatible with DcqlMatcher.
//     *
//     * @param customCredentials A list of pairs, where each pair contains the
//     *                          detection result and the digital credential data.
//     * @param idExtractor A function to extract a unique identifier string for
//     *                    each DigitalCredential. This is crucial as the base
//     *                    DigitalCredential doesn't have an ID field.
//     * @return A list of Credentials ready for the DcqlMatcher.
//     */
//    fun adaptCredentials(
//        customCredentials: List<Pair<CredentialDetectorTypes.CredentialDetectionResult, DigitalCredential>>,
//        idExtractor: (DigitalCredential) -> String, // Function to get the ID
//    ): List<DcqlCredential> {
//        return customCredentials.mapNotNull { (detectionResult, digitalCredential) ->
//            val formatString = mapDetectionResultToFormat(detectionResult)
//
//            if (formatString == null) {
//                null // Skip credentials we can't map
//            } else {
//                val id = idExtractor(digitalCredential) // Extract the ID using the provided function
//                AdaptedCredential(
//                    id = id,
//                    format = formatString,
//                    data = digitalCredential.credentialData,
//                    //issuer = digitalCredential.issuer,
//                    // subject is not directly used by the current matcher interface
//                )
//            }
//        }
//    }
//
//    /**
//     * Maps the detailed detection result to a standard DCQL format string.
//     */
//    private fun mapDetectionResultToFormat(
//        result: CredentialDetectorTypes.CredentialDetectionResult,
//    ): String? {
//        return when (result.credentialPrimaryType) {
//            CredentialDetectorTypes.CredentialPrimaryDataType.W3C -> when (result.signaturePrimary) {
//                CredentialDetectorTypes.SignaturePrimaryType.JWT, CredentialDetectorTypes.SignaturePrimaryType.SDJWT -> "jwt_vc_json"
//                CredentialDetectorTypes.SignaturePrimaryType.DATA_INTEGRITY_PROOF -> "ldp_vc"
//                CredentialDetectorTypes.SignaturePrimaryType.UNSIGNED -> { "ldp_vc" }
//                CredentialDetectorTypes.SignaturePrimaryType.COSE -> TODO("implement cose")
//            }
//            CredentialDetectorTypes.CredentialPrimaryDataType.SDJWTVC -> "dc+sd-jwt"
//            CredentialDetectorTypes.CredentialPrimaryDataType.MDOCS -> "mso_mdoc"
//        }
//    }
//}
