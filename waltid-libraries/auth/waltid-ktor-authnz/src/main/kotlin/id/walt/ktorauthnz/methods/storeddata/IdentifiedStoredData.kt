package id.walt.ktorauthnz.methods.storeddata

import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import kotlinx.serialization.Serializable

@Serializable
data class IdentifiedStoredData(val identifier: AccountIdentifier, val data: AuthMethodStoredData)
