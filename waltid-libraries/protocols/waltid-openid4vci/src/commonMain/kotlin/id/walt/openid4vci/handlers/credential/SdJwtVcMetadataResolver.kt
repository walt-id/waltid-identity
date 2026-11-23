package id.walt.openid4vci.handlers.credential

import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.sdjwt.metadata.type.PublishedTypeMetadata
import id.walt.sdjwt.metadata.type.ResolvedTypeMetadata

/** Select a configured issuer source once per signing attempt. Null means deliberately unconfigured. */
fun interface SdJwtVcMetadataResolver {
    suspend fun resolve(configuration: CredentialConfiguration): SdJwtVcMetadataContext?
}

/** An immutable publication/chain binding and operation-scoped source, owned by the SD-JWT handler. */
interface SdJwtVcMetadataContext : AutoCloseable {
    val publication: PublishedTypeMetadata
    val resolved: ResolvedTypeMetadata
    suspend fun readDocument(uri: String): ByteArray
}
