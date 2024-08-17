package id.walt.crypto.keys.oci

import kotlinx.serialization.Serializable

@Serializable
actual data class OCIsdkMetadata actual constructor(
    actual val vaultId: String,
    actual val compartmentId: String,

    )
