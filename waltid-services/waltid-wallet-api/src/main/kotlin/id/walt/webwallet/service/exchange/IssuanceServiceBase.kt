package id.walt.webwallet.service.exchange

import cbor.Cbor
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.JwsUtils.decodeJwsOrSdjwt
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.mdoc.dataelement.toDataElement
import id.walt.mdoc.doc.MDoc
import id.walt.mdoc.issuersigned.IssuerSigned
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.OfferedCredential
import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft04
import id.walt.webwallet.utils.WalletHttpClients
import io.klogging.Klogger
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

abstract class IssuanceServiceBase {

    protected val http = WalletHttpClients.getHttpClient()
    protected abstract val logger: Klogger

    protected fun parseOfferParams(offerURL: String) = Url(offerURL).parameters.toMap()

    protected suspend fun getCredentialData(
        processedOffer: ProcessedCredentialOffer,
        manifest: JsonObject?,
    ): CredentialDataResult {
        val credential = processedOffer.credentialResponse.credential!!.jsonPrimitive.content

        val credentialFormat = processedOffer.credentialRequest?.format
            ?: requireNotNull(processedOffer.credentialResponse.format) {
                "No credential format specified in either the " +
                        "credential request, or credential response"
            }

        return when (credentialFormat) {
            CredentialFormat.mso_mdoc -> getMdocCredentialDataResult(
                processedOffer,
                credential,
            )

            else -> getDefaultCredentialDataResult(
                credential,
                manifest,
                credentialFormat,
            )
        }.also {
            logger.debug { "Generated from processed credential offer: $it" }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun getMdocCredentialDataResult(
        processedOffer: ProcessedCredentialOffer,
        credential: String,
    ): CredentialDataResult {
        val credentialEncoding =
            processedOffer.credentialResponse.customParameters!!["credential_encoding"]?.jsonPrimitive?.content
                ?: "issuer-signed"
        logger.debug { "Parsed credential encoding: $credentialEncoding" }

        val docType =
            processedOffer.credentialRequest?.docType
                ?: throw IllegalArgumentException("Credential request has no docType property")
        logger.debug { "Parsed docType: $docType" }

        val mDoc = when (credentialEncoding) {
            "issuer-signed" -> MDoc(
                docType.toDataElement(), IssuerSigned.fromMapElement(
                    Cbor.decodeFromByteArray(credential.base64UrlDecode())
                ), null
            )

            else -> throw IllegalArgumentException("Invalid credential encoding: $credentialEncoding")
        }
        // TODO: review ID generation for mdoc
        return CredentialDataResult(
            id = randomUUIDString(),
            document = mDoc.toCBORHex(),
            type = docType,
            format = CredentialFormat.mso_mdoc,
        )
    }

    private suspend fun getDefaultCredentialDataResult(
        credential: String,
        manifest: JsonObject?,
        credentialFormat: CredentialFormat,
    ): CredentialDataResult {
        val credentialParts = credential.decodeJwsOrSdjwt()
        logger.debug { "Parsed JWT-based credential parts: $credentialParts" }

        val typ = credentialParts.jwsParts.header["typ"]?.jsonPrimitive?.content?.lowercase()
            ?: throw IllegalArgumentException(
                "JWT-based credential $credential does not have " +
                        "`typ` value specified in its header"
            )
        logger.debug { "Parsed JWT-based credential type: $typ" }

        val vc = credentialParts.jwsParts.payload["vc"]?.jsonObject ?: credentialParts.jwsParts.payload
        logger.debug { "Parsed JWT-based vc payload: $vc" }

        val credentialId = vc["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: randomUUIDString()

        val disclosures = credentialParts.sdJwtDisclosures
        logger.debug { "Parsed disclosures (size = ${disclosures.size}): $disclosures" }

        return CredentialDataResult(
            id = credentialId,
            document = credentialParts.jwsParts.toString(),
            disclosures = credentialParts.sdJwtDisclosuresString()
                .drop(1) // remove the first '~'
                .dropLast(1), // remove the last '~'
            manifest = manifest?.toString(),
            type = typ,
            format = credentialFormat,
        )
    }

    suspend fun resolveVct(vct: String): SdJwtVcTypeMetadataDraft04 {
        val authority = Url(vct).protocolWithAuthority
        val response = http.get("$authority/.well-known/vct${vct.substringAfter(authority)}")

        require(response.status.isSuccess()) { "VCT URL returns error: ${response.status}" }

        return response.body<JsonObject>().let { SdJwtVcTypeMetadataDraft04.fromJSON(it) }
    }

    fun isKeyProofRequiredForOfferedCredential(offeredCredential: OfferedCredential) =
        // Use key proof if the supported cryptographic binding method is not empty, doesn't contain DID and contains cose_key or jwk
        (offeredCredential.cryptographicBindingMethodsSupported != null &&
                (offeredCredential.cryptographicBindingMethodsSupported!!.contains("cose_key") ||
                        offeredCredential.cryptographicBindingMethodsSupported!!.contains("jwk")) &&
                !offeredCredential.cryptographicBindingMethodsSupported!!.contains("did"))
}