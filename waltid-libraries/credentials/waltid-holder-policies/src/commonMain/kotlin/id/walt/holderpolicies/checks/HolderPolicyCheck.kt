package id.walt.holderpolicies.checks

import id.walt.credentials.formats.DigitalCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
sealed interface HolderPolicyCheck {
    suspend fun matchesCredentials(credentials: Flow<DigitalCredential>): Boolean
}
