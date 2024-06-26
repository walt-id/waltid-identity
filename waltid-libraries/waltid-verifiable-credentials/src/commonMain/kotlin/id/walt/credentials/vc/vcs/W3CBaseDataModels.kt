package id.walt.credentials.vc.vcs

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object W3CBaseDataModels {

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
