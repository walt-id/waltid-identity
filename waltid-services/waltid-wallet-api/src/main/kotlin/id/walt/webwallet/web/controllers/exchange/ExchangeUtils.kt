@file:OptIn(ExperimentalTime::class)

package id.walt.webwallet.web.controllers.exchange

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.oid4vc.data.CredentialFormat
import id.walt.sdjwt.KeyBindingJwt
import id.walt.sdjwt.KeyBindingJwt.Companion.getSdHash
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.oidc4vc.CredentialFilterUtils
import id.walt.webwallet.web.controllers.exchange.models.oid4vp.IETFSdJwtVpProofParameters
import id.walt.webwallet.web.controllers.exchange.models.oid4vp.W3cJwtVpProofParameters
import kotlin.time.Clock
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

object ExchangeUtils {

    fun getFirstAuthKeyIdFromDidDocument(
        didDocument: String,
    ) = Json.decodeFromString<JsonObject>(didDocument).let { document ->
        checkNotNull(document["authentication"]) { "no authentication relationship defined in resolved did document" }
        check(document["authentication"] is JsonArray) { "resolved did document is invalid: authentication relationship ${document["authentication"]} is not a json array" }
        check(document["authentication"]!!.jsonArray.size > 0) { "resolved did document's authentication relationship is an empty json array" }
        when (val firstAuthRelEntry = document["authentication"]!!.jsonArray.first()) {
            is JsonObject -> {
                checkNotNull(firstAuthRelEntry["id"]) { "resolved did document's authentication relationship first entry does not contain an id property" }
                check(
                    (firstAuthRelEntry["id"] is JsonPrimitive) &&
                            (firstAuthRelEntry["id"]!!.jsonPrimitive.isString)
                ) {
                    "id property of the first entry of the authentication relationship of the resolved did document is not a string"
                }
                firstAuthRelEntry["id"]!!.jsonPrimitive.content
            }

            is JsonPrimitive -> {
                check(firstAuthRelEntry.isString) { "first entry of the authentication relationship of the resolved did document is encoded as a reference to a verification method but is not of type string" }
                firstAuthRelEntry.content
            }

            else -> {
                throw IllegalArgumentException("resolved did document's authentication relationship first entry is neither a json object nor a json primitive of type string")
            }
        }
    }

    fun getW3cJwtVpProofParametersFromWalletCredentials(
        did: String,
        didAuthKeyId: String,
        presentationId: String,
        audience: String,
        nonce: String?,
        credentials: List<WalletCredential>,
        disclosures: Map<String, List<String>>?,
    ) = CredentialFilterUtils.getJwtVcList(
        credentials,
        disclosures,
    ).takeIf { it.isNotEmpty() }?.let { jwtVcList ->
        W3cJwtVpProofParameters(
            header = mapOf(
                "kid" to didAuthKeyId.toJsonElement(),
                "typ" to "JWT".toJsonElement(),
            ),
            payload = mapOf(
                "sub" to did.toJsonElement(),
                "nbf" to Clock.System.now().minus(1.minutes).epochSeconds.toJsonElement(),
                "iat" to Clock.System.now().epochSeconds.toJsonElement(),
                "jti" to presentationId.toJsonElement(),
                "iss" to did.toJsonElement(),
                "aud" to audience.toJsonElement(),
                "nonce" to (nonce ?: "").toJsonElement(),
                "vp" to mapOf(
                    "@context" to listOf("https://www.w3.org/2018/credentials/v1").toJsonElement(),
                    "type" to listOf("VerifiablePresentation").toJsonElement(),
                    "id" to presentationId.toJsonElement(),
                    "holder" to did.toJsonElement(),
                    "verifiableCredential" to jwtVcList.toJsonElement()
                ).toJsonElement(),
            ).filterValues { it.toString().isNotBlank() },
        )
    }

    fun getIETFJwtVpProofParametersFromWalletCredentials(
        keyId: String,
        audience: String,
        nonce: String?,
        credentials: List<WalletCredential>,
        disclosures: Map<String, List<String>>?,
    ) = credentials.filter { it.format == CredentialFormat.sd_jwt_vc }.map { credential ->
        val serializedVcWithDisclosures = listOf(
            credential.document
        ).plus(
            getDisclosures(
                disclosures = disclosures,
                credentialId = credential.id,
            )
        ).joinToString(separator = "")
        IETFSdJwtVpProofParameters(
            credentialId = credential.id,
            sdJwtVc = serializedVcWithDisclosures,
            header = mapOf(
                "kid" to keyId.toJsonElement(),
                "typ" to KeyBindingJwt.KB_JWT_TYPE.toJsonElement(),
            ),
            payload = mapOf(
                "iat" to Clock.System.now().epochSeconds.toJsonElement(),
                "aud" to audience.toJsonElement(),
                "nonce" to (nonce ?: "").toJsonElement(),
                "sd_hash" to getSdHash(serializedVcWithDisclosures).toJsonElement(),
            ).filterValues { it.toString().isNotBlank() },
        )
    }.takeIf { it.isNotEmpty() }

    private fun getDisclosures(
        disclosures: Map<String, List<String>>?,
        credentialId: String
    ) = if (disclosures?.containsKey(credentialId) == true) {
        "~${disclosures[credentialId]!!.joinToString("~")}~"
    } else "~"
}
