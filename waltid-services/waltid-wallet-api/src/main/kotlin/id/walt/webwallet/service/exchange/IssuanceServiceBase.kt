package id.walt.webwallet.service.exchange

import cbor.Cbor
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.JwsUtils.decodeJwsOrSdjwt
import id.walt.mdoc.dataelement.toDataElement
import id.walt.mdoc.doc.MDoc
import id.walt.mdoc.issuersigned.IssuerSigned
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.randomUUID
import id.walt.sdjwt.SDJWTVCTypeMetadata
import id.walt.webwallet.service.oidc4vc.TestCredentialWallet
import id.walt.webwallet.utils.WalletHttpClients
import io.klogging.Klogger
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
    protected abstract val logger: Klogger

    protected fun parseOfferParams(offerURL: String) = Url(offerURL).parameters.toMap()

    protected suspend fun getCredentialIssuerOpenIDMetadata(
        issuerURL: String,
        credentialWallet: TestCredentialWallet,
    ): OpenIDProviderMetadata {
        logger.debug { "// get issuer metadata" }
        val providerMetadataUri =
            credentialWallet.getCIProviderMetadataUrl(issuerURL)
        logger.debug { "Getting provider metadata from: $providerMetadataUri" }
        val providerMetadataResult = http.get(providerMetadataUri)
        logger.debug { "Provider metadata returned: ${providerMetadataResult.bodyAsText()}" }
        return providerMetadataResult
            .body<JsonObject>()
            .let {
                OpenIDProviderMetadata.fromJSON(it)
            }
    }

    protected suspend fun issueTokenRequest(
        tokenURL: String,
        req: TokenRequest,
    ) = http.submitForm(
        tokenURL, formParameters = parametersOf(req.toHttpParameters())
    ).let {
        logger.debug { "Raw TokenResponse: $it" }
        it.body<JsonObject>().let { TokenResponse.fromJSON(it) }
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

    @OptIn(ExperimentalSerializationApi::class)
    protected suspend fun getCredentialData(
        processedOffer: ProcessedCredentialOffer, manifest: JsonObject?,
    ) = let {
        val credential = processedOffer.credentialResponse.credential!!.jsonPrimitive.content

        when (val credentialFormat = processedOffer.credentialResponse.format) {
            CredentialFormat.mso_mdoc -> {
                val credentialEncoding =
                    processedOffer.credentialResponse.customParameters["credential_encoding"]?.jsonPrimitive?.content
                        ?: "issuer-signed"
                val docType =
                    processedOffer.credentialRequest?.docType
                        ?: throw IllegalArgumentException("Credential request has no docType property")
                val format =
                    processedOffer.credentialResponse.format
                        ?: throw IllegalArgumentException("Credential response has no format property")
                val mdoc = when (credentialEncoding) {
                    "issuer-signed" -> MDoc(
                        docType.toDataElement(), IssuerSigned.fromMapElement(
                            Cbor.decodeFromByteArray(credential.base64UrlDecode())
                        ), null
                    )

                    else -> throw IllegalArgumentException("Invalid credential encoding: $credentialEncoding")
                }
                // TODO: review ID generation for mdoc
                CredentialDataResult(randomUUID(), mdoc.toCBORHex(), type = docType, format = format)
            }

            else -> {
                val credentialParts = credential.decodeJwsOrSdjwt()
                logger.debug { "Got credential: $credentialParts" }

                val typ = credentialParts.jwsParts.header["typ"]?.jsonPrimitive?.content?.lowercase()
                    ?: error("Credential does not have `typ`!")

                val vc = credentialParts.jwsParts.payload["vc"]?.jsonObject ?: credentialParts.jwsParts.payload
                val credentialId = vc["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: randomUUID()

                val disclosures = credentialParts.sdJwtDisclosures
                logger.debug { "Disclosures (${disclosures.size}): $disclosures" }

                CredentialDataResult(
                    id = credentialId,
                    document = credentialParts.jwsParts.toString(),
                    disclosures = credentialParts.sdJwtDisclosuresString().drop(1), // remove first '~'
                    manifest = manifest?.toString(),
                    type = typ,
                    format = credentialFormat ?: error("No credential format")
                )
            }
        }
    }

    suspend fun resolveVct(vct: String): SDJWTVCTypeMetadata {
        val authority = Url(vct).protocolWithAuthority
        val response = http.get("$authority/.well-known/vct${vct.substringAfter(authority)}")

        require(response.status.isSuccess()) {"VCT URL returns error: ${response.status}"}

        return response.body<JsonObject>().let { SDJWTVCTypeMetadata.fromJSON(it) }
    }
}