package id.walt.verifier.openid.models.dcql.meta

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * No specific meta parameters defined for this format in the base spec,
 * but allows for profile-specific extensions.
 */
@Serializable
@SerialName("NoMeta") // Discriminator for serialization if needed, though often context-driven
object NoMeta : CredentialQueryMeta // For formats without defined meta or when meta is absent
