package id.walt.dcql.models.meta

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * No specific meta parameters defined for this format in the base spec,
 * but allows for profile-specific extensions.
 */
@Serializable
@SerialName("NoMeta")
object NoMeta : CredentialQueryMeta {
    override val format = null


}
