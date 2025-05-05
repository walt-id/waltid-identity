package id.walt.webwallet.web.controllers.exchange.models.oid4vp

import kotlinx.serialization.Serializable

@Serializable
data class IETFSdJwtVpTokenProof(
    val credentialId: String,
    val sdJwtVc: String,
    val vpTokenProof: String,
)