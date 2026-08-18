package id.walt.rpcert.cli.util

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.rpcert.issuance.RelyingPartyRegistrationCertificateIssuer
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class NoRegistrationCertificateFoundException(message: String) : IllegalArgumentException(message)

/**
 * Finds the Wallet-Relying Party Registration Certificate JWT (`rc-wrp+jwt`) among an Authorization
 * Request's `verifier_info` attestations. There is no standard OpenID4VP field dedicated to the
 * WRPRC; `verifier_info` is the spec's general-purpose verifier-attestation extension point, used
 * here as its transport.
 */
object VerifierInfoCertExtractor {

    fun extractRegistrationCertificateJwt(authorizationRequest: AuthorizationRequest): String {
        val candidates = authorizationRequest.verifierInfo.orEmpty().filter { item ->
            runCatching { item.data.decodeJws().header["typ"]?.jsonPrimitive?.contentOrNull }
                .getOrNull() == RelyingPartyRegistrationCertificateIssuer.JWT_TYPE
        }

        return when (candidates.size) {
            0 -> throw NoRegistrationCertificateFoundException(
                "No registration certificate (typ '${RelyingPartyRegistrationCertificateIssuer.JWT_TYPE}') found in verifier_info"
            )

            1 -> candidates.single().data

            else -> throw NoRegistrationCertificateFoundException(
                "Multiple registration certificates found in verifier_info; expected exactly one"
            )
        }
    }
}
