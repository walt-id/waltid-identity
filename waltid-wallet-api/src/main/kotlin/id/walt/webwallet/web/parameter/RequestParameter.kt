package id.walt.webwallet.web.parameter

import kotlinx.serialization.Serializable

interface RequestParameter

@Serializable
class NoteRequestParameter(
    val note: String,
) : RequestParameter

@Serializable
class CredentialRequestParameter(
    val credentialId: String,
    val parameter: RequestParameter? = null
) : RequestParameter