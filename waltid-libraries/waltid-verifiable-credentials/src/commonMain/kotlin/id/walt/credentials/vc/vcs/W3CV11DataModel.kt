package id.walt.credentials.vc.vcs


import id.walt.credentials.vc.vcs.CredentialDataModel.Companion.w3cJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * W3C V2.0
 * https://www.w3.org/TR/vc-data-model-2.0/
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class W3CV11DataModel(
    @SerialName("@context") val context: List<String> = defaultContext,
    val type: List<String> = listOf("VerifiableCredential"), // [VerifiableCredential, ExampleAlumniCredential]
    val credentialSubject: JsonObject,
    val id: String? = null, // http://university.example/credentials/1872
    val issuer: String? = null, // https://university.example/issuers/565049
    val issuanceDate: String? = null, // 2010-01-01T19:23:24Z
    val expirationDate: String? = null,
    val credentialStatus: W3CBaseDataModels.CredentialStatus? = null,
    val termsOfUse: W3CBaseDataModels.TermsOfUse? = null,
) : CredentialDataModel {

    companion object : W3CMetadata {
        override val defaultContext = listOf("https://www.w3.org/2018/credentials/v1")
    }

    @Serializable
    data class CredentialSubject(
        val id: String // did:example:ebfeb1f712ebc6f1c276e12ec21
    )

    override fun encodeToJsonObject(): JsonObject = w3cJson.encodeToJsonElement(this).jsonObject
}
