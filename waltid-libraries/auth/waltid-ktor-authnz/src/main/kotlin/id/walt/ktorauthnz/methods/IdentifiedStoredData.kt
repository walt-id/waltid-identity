package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.methods.data.AuthMethodStoredData
import kotlinx.serialization.Serializable

@Serializable
data class IdentifiedStoredData(val identifier: AccountIdentifier, val data: AuthMethodStoredData)
