package id.walt.webwallet.web.parameter

import id.walt.oid4vc.data.CredentialFormat
import kotlinx.serialization.Serializable

interface RequestParameter

@Serializable
class NoteRequestParameter(
    val note: String,
) : RequestParameter

@Serializable
class CredentialRequestParameter(
    val credentialId: String,
    val parameter: RequestParameter? = null,
) : RequestParameter

@Serializable
data class StoreCredentialRequest(
    val document: String,
    val disclosures: String? = null,
    val format: CredentialFormat = CredentialFormat.jwt_vc_json,
) : RequestParameter
