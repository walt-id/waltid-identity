package id.walt.credentials.vc.vcs


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * W3C V2.0
 * https://www.w3.org/TR/vc-data-model-2.0/
 */
@Serializable
data class W3CV2DataModel(
    @SerialName("@context")
    val context: List<String> = listOf("https://www.w3.org/ns/credentials/v2"), // [https://www.w3.org/ns/credentials/v2, https://www.w3.org/ns/credentials/examples/v2]
    val type: List<String> = listOf("VerifiableCredential"), // [VerifiableCredential, ExampleAlumniCredential]
    val credentialSubject: JsonObject,
    val id: String? = null, // http://university.example/credentials/1872
    val issuer: String? = null, // https://university.example/issuers/565049
    val validFrom: String? = null, // 2010-01-01T19:23:24Z
    val validUntil: String? = null,
    val credentialStatus: CredentialStatus? = null,
    val termsOfUse: TermsOfUse? = null,
) {

    @Serializable
    data class CredentialSubject(
        val id: String // did:example:ebfeb1f712ebc6f1c276e12ec21
    )

    @Serializable
    data class TermsOfUse(
        val id: String, // "https://api-test.ebsi.eu/trusted-issuers-registry/v4/issuers/did:ebsi:zz7XsC9ixAXuZecoD9sZEM1/attributes/7201d95fef05f72667f5454c2192da2aa30d9e052eeddea7651b47718d6f31b0
        val type: String // "IssuanceCertificate"
    )

    @Serializable
    data class CredentialStatus(
        val id: String, // https://university.example/credentials/status/3#94567
        val type: String, // StatusList2021Entry
        val statusPurpose: String, // revocation
        val statusListIndex: String, // 94567
        val statusListCredential: String // https://university.example/credentials/status/3
    )
}
