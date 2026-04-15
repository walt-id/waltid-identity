package id.walt.issuer2.models

import id.walt.openid4vci.offers.TxCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.minutes

@Serializable
data class CredentialOfferCreateRequest(
    val profileId: String,
    val authMethod: AuthenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
    val issuerStateMode: IssuerStateMode = IssuerStateMode.OMIT,
    val valueMode: CredentialOfferValueMode = CredentialOfferValueMode.BY_REFERENCE,
    val expiresInSeconds: Long = 5.minutes.inWholeSeconds,
    val txCode: TxCode? = null,
    val txCodeValue: String? = null,
    val runtimeOverrides: CredentialOfferRuntimeOverrides? = null,
)

@Serializable
data class CredentialOfferRuntimeOverrides(
    val issuerDid: String? = null,
    val issuerKey: JsonObject? = null,
    val x5Chain: List<String>? = null,
    val credentialData: JsonObject? = null,
    val mapping: JsonObject? = null,
)
