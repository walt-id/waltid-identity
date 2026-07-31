package id.walt.rpcert.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wallet-Relying Party Registration Certificate (WRPRC), as specified in
 * ETSI TS 119 475 V1.2.1, clause 5.2.4, Table 7 (+ Table 8/9/10 for credential/optional attributes).
 * https://www.etsi.org/deliver/etsi_ts/119400_119499/119475/01.02.01_60/ts_119475v010201p.pdf
 *
 */
@Serializable
data class RelyingPartyRegistrationCertificate(
    val name: String,
    @SerialName("sub_ln")
    val subLn: String? = null,
    @SerialName("sub_gn")
    val subGn: String? = null,
    @SerialName("sub_fn")
    val subFn: String? = null,
    val sub: String,
    val country: String,
    @SerialName("registry_uri")
    val registryUri: String,
    @SerialName("srv_description")
    val srvDescription: List<List<MultiLangString>>,
    val entitlements: List<String>,
    @SerialName("privacy_policy")
    val privacyPolicy: String,
    @SerialName("info_uri")
    val infoUri: String? = null,
    @SerialName("support_uri")
    val supportUri: String? = null,
    @SerialName("supervisory_authority")
    val supervisoryAuthority: SupervisoryAuthority,
    @SerialName("policy_id")
    val policyId: List<String>? = null,
    @SerialName("certificate_policy")
    val certificatePolicy: String? = null,
    val iat: Long,
    val exp: Long? = null,
    val status: WalletRelyingPartyRegistrationCertificateStatus? = null,
    val purpose: List<MultiLangString>,
    val credentials: List<RegistrationCertificateCredential>,
    @SerialName("provides_attestations")
    val providesAttestations: List<RegistrationCertificateCredential>? = null,
    val intermediary: IntermediaryReference? = null,
    @SerialName("public_body")
    val publicBody: Boolean? = null,
)

@Serializable
data class MultiLangString(
    val lang: String,
    val content: String,
)

@Serializable
data class SupervisoryAuthority(
    val email: String? = null,
    val phone: String? = null,
    val uri: String? = null,
)

@Serializable
data class WalletRelyingPartyRegistrationCertificateStatus(
    @SerialName("status_list")
    val statusList: StatusList,
) {
    @Serializable
    data class StatusList(
        val idx: Int,
        val uri: String,
    )
}

@Serializable
data class RegistrationCertificateCredential(
    val format: String,
    val meta: Map<String, JsonElement>,
    val claim: List<Claim>? = null,
) {
}

@Serializable
data class Claim(
    val path: List<String>,
    val values: List<String>? = null,
)

@Serializable
data class IntermediaryReference(
    val sub: String,
    val sname: String? = null,
    val name: String? = null,
)