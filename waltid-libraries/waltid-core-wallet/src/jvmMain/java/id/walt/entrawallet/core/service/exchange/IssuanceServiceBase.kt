package id.walt.entrawallet.core.service.exchange

import cbor.Cbor
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.JwsUtils.decodeJwsOrSdjwt
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.entrawallet.core.service.oidc4vc.TestCredentialWallet
import id.walt.mdoc.dataelement.toDataElement
import id.walt.mdoc.doc.MDoc
import id.walt.mdoc.issuersigned.IssuerSigned
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.OfferedCredential
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.TokenResponse
import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft04
import id.walt.webwallet.utils.WalletHttpClients
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

abstract class IssuanceServiceBase {

    protected val http = WalletHttpClients.getHttpClient()
    protected abstract val logger: io.github.oshai.kotlinlogging.KLogger

    protected fun parseOfferParams(offerURL: String) = Url(offerURL).parameters.toMap()

    protected suspend fun getCredentialIssuerOpenIDMetadata(
        issuerURL: String,
        credentialWallet: TestCredentialWallet,
    ): OpenIDProviderMetadata {
        logger.debug { "// get issuer metadata" }
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(issuerURL)
        logger.debug { "Getting provider metadata from: $providerMetadataUri" }
        val providerMetadataResult = http.get(providerMetadataUri).bodyAsText()
        logger.debug { "Provider metadata returned: $providerMetadataResult" }
        return OpenIDProviderMetadata.fromJSONString(providerMetadataResult)
    }

    protected suspend fun issueTokenRequest(
        tokenURL: String,
        req: TokenRequest,
    ) = http.submitForm(
        tokenURL, formParameters = parametersOf(req.toHttpParameters())
    ).let { rawResponse ->
        logger.debug { "Raw TokenResponse: $rawResponse" }
        rawResponse.body<JsonObject>().let {
            TokenResponse.fromJSON(it)
        }
    }

    protected fun validateTokenResponse(
        tokenResponse: TokenResponse,
    ) {
        require(tokenResponse.isSuccess) {
            "token request failed: ${tokenResponse.error} ${tokenResponse.errorDescription}"
        }
        //there has to be an access token in the response, otherwise we are unable
        //to invoke the credential endpoint
        requireNotNull(tokenResponse.accessToken) {
            "invalid Authorization Server token response: no access token included in the response: $tokenResponse "
        }
    }

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
    private fun getMdocCredentialDataResult(
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

    private fun getDefaultCredentialDataResult(
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
            disclosures = credentialParts.sdJwtDisclosuresString().drop(1), // remove first '~'
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
        // Use key proof if supported cryptographic binding method is not empty, doesn't contain did and contains cose_key or jwk
        (offeredCredential.cryptographicBindingMethodsSupported != null &&
                (offeredCredential.cryptographicBindingMethodsSupported!!.contains("cose_key") ||
                        offeredCredential.cryptographicBindingMethodsSupported!!.contains("jwk")) &&
                !offeredCredential.cryptographicBindingMethodsSupported!!.contains("did"))
}
