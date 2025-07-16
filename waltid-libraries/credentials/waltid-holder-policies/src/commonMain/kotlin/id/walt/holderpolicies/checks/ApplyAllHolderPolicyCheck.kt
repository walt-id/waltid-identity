package id.walt.holderpolicies.checks

import id.walt.credentials.formats.DigitalCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("all")
class ApplyAllHolderPolicyCheck : HolderPolicyCheck {
    override suspend fun matchesCredentials(credentials: Flow<DigitalCredential>): Boolean = true
}
