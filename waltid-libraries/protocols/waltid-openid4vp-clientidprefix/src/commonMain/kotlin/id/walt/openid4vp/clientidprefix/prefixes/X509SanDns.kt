package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.openid4vp.clientidprefix.extractSanDnsNamesFromDer
import id.walt.verifier.openid.models.authorization.ClientMetadata
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handles `x509_san_dns` prefix per OpenID4VP 1.0, Section 5.9.3.
 */
@Serializable
data class X509SanDns(val dnsName: String, override val rawValue: String) : ClientId {

    companion object {
        private val log = KotlinLogging.logger { }
    }

    init {
        // A simple regex to check for a plausible DNS name format.
        val dnsRegex = "^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?.)+[a-zA-Z]{2,6}$".toRegex()
        require(dnsRegex.matches(dnsName)) { "Invalid DNS name format for x509_san_dns." }
    }

    suspend fun authenticateX509SanDns(clientId: X509SanDns, context: RequestContext): ClientValidationResult {
        val jws = context.requestObjectJws
            ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)

        //return runCatching {
        val decodedJws = runCatching { jws.decodeJws() }.getOrElse { return ClientValidationResult.Failure(ClientIdError.InvalidJws) }

        val x5cHeader = decodedJws.header["x5c"]?.jsonArray
            ?: return ClientValidationResult.Failure(ClientIdError.MissingX5cHeader)

        val leafCertDer = x5cHeader.firstOrNull()?.jsonPrimitive?.content?.decodeFromBase64()
            ?: return ClientValidationResult.Failure(ClientIdError.EmptyX5cHeader)

        // 1. Verify JWS signature using the certificate's public key.
        val key = JWKKey.importFromDerCertificate(leafCertDer).getOrThrow()
        log.trace { "Imported key from leaf cer der for X509SanDns: $key" }

        key.verifyJws(jws).getOrThrow()

        // 2. Extract SANs using the isolated JCA utility function.
        val sans = extractSanDnsNamesFromDer(leafCertDer).getOrElse {
            return ClientValidationResult.Failure(ClientIdError.CannotExtractSanDnsNamesFromDer)
        }

        // 3. Check if the client_id's DNS name is in the SAN list.
        if (clientId.dnsName !in sans) {
            return ClientValidationResult.Failure(ClientIdError.SanDnsMismatch(clientId.dnsName, sans))
        }

        val metadataJson = context.clientMetadataJson
            ?: return ClientValidationResult.Failure(ClientIdError.MissingClientMetadata)


        return runCatching { ClientMetadata.fromJson(metadataJson).getOrThrow() }
            .fold(
                onSuccess = { ClientValidationResult.Success(it) },
                onFailure = { ClientValidationResult.Failure(ClientIdError.InvalidSignature) }
            )
    }
}
