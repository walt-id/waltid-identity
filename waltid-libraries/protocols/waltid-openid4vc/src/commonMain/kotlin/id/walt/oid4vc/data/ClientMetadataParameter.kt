package id.walt.oid4vc.data

@ConsistentCopyVisibility
data class ClientMetadataParameter private constructor(
    val clientMetadata: OpenIDClientMetadata?,
    val clientMetadataUri: String?
) {
    companion object {
        fun fromClientMetadata(clientMetadata: OpenIDClientMetadata) =
            ClientMetadataParameter(clientMetadata, null)

        fun fromClientMetadataUri(clientMetadataUri: String) =
            ClientMetadataParameter(null, clientMetadataUri)
    }
}
